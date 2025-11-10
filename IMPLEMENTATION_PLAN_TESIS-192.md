# Plan de Implementación: TESIS-192 - Fix Deployment Sync Workflow Change Bug

## 🐛 Problema Identificado

### Escenario del Bug:
1. Usuario configura nombre de workflow incorrecto (ej: `"wrong-deploy.yml"`)
2. Sincronización corre → No encuentra deployments → **Actualiza SyncStatus con timestamp actual**
3. Usuario corrige el nombre a `"deploy.yml"` (correcto)
4. Sincronización corre → Busca solo desde último timestamp → **Pierde históricos anteriores**

### Causas Raíz:
- `SyncStatus` key no incluye el workflow filename → `"DEPLOYMENT_SYNC_{repoName}"`
- No se detecta cuando cambia el workflow configurado
- `SyncStatus` se actualiza incluso cuando no hay deployments
- No hay mecanismo para resetear sincronización cuando cambia configuración

## 🎯 Solución Propuesta

### Combinación de Opciones 1 + 3:

**Opción 1**: SyncStatus key incluye workflow filename
- Key format: `"DEPLOYMENT_SYNC_{repoName}_{workflowFileName}"`
- Cada workflow tiene su propio estado de sincronización
- Cambiar workflow = nuevo SyncStatus = captura históricos

**Opción 3**: Detectar cambio de workflow y resetear
- Agregar campo `lastSyncedWorkflowFile` a `RepositoryConfig`
- Comparar workflow actual vs último sincronizado
- Si cambia → Log del cambio, actualizar campo, permitir búsqueda completa
- Previene pérdida de datos por errores de configuración

## 📋 Tareas de Implementación

### 1. Modificar Entidad RepositoryConfig
**Archivo**: `src/main/java/org/grubhart/pucp/tesis/module_domain/RepositoryConfig.java`

**Cambios**:
```java
@Column(name = "last_synced_workflow_file")
private String lastSyncedWorkflowFile;

// Getter y Setter
public String getLastSyncedWorkflowFile() {
    return lastSyncedWorkflowFile;
}

public void setLastSyncedWorkflowFile(String lastSyncedWorkflowFile) {
    this.lastSyncedWorkflowFile = lastSyncedWorkflowFile;
}
```

### 2. Database Schema Update
**Nota**: Spring Boot con `ddl-auto=create-drop` regenerará automáticamente el schema.

Para ambientes productivos (futuro), agregar migration:
```sql
ALTER TABLE repository_config
ADD COLUMN last_synced_workflow_file VARCHAR(255);
```

### 3. Modificar DeploymentSyncService

**Archivo**: `src/main/java/org/grubhart/pucp/tesis/module_collector/service/DeploymentSyncService.java`

#### 3.1. Cambiar construcción de SyncStatus key (Opción 1)

**Línea 76**:
```java
// ANTES:
String syncKey = JOB_NAME + "_" + repoName;

// DESPUÉS:
String syncKey = JOB_NAME + "_" + repoName + "_" + workflowFileName;
```

**Línea 129**:
```java
// ANTES:
SyncStatus status = new SyncStatus(JOB_NAME + "_" + repoName, LocalDateTime.now());

// DESPUÉS:
SyncStatus status = new SyncStatus(
    JOB_NAME + "_" + repoName + "_" + workflowFileName,
    LocalDateTime.now()
);
```

#### 3.2. Agregar detección de cambio de workflow (Opción 3)

**Nuevo método privado**:
```java
/**
 * Detects if the workflow filename has changed and resets sync status if needed.
 * Returns true if workflow changed (sync should start from beginning).
 */
private boolean detectAndHandleWorkflowChange(
        RepositoryConfig repoConfig,
        String currentWorkflow) {

    String lastWorkflow = repoConfig.getLastSyncedWorkflowFile();

    // First time syncing this repo
    if (lastWorkflow == null) {
        log.info("First sync for repository {}. Setting workflow: {}",
                 repoConfig.getRepositoryUrl(), currentWorkflow);
        repoConfig.setLastSyncedWorkflowFile(currentWorkflow);
        repositoryConfigRepository.save(repoConfig);
        return true; // Treat as change to force full sync
    }

    // Workflow changed
    if (!currentWorkflow.equals(lastWorkflow)) {
        log.warn("Workflow filename changed for repository {} from '{}' to '{}'. " +
                 "Sync will restart from beginning to capture historical deployments.",
                 repoConfig.getRepositoryUrl(), lastWorkflow, currentWorkflow);

        repoConfig.setLastSyncedWorkflowFile(currentWorkflow);
        repositoryConfigRepository.save(repoConfig);
        return true; // Force full sync
    }

    return false; // No change
}
```

**Modificar `syncDeploymentsForRepository`**:
```java
private void syncDeploymentsForRepository(
        String owner,
        String repoName,
        String workflowFileName,
        RepositoryConfig repositoryConfig) {

    // Detect workflow change BEFORE getting sync status
    boolean workflowChanged = detectAndHandleWorkflowChange(
        repositoryConfig,
        workflowFileName
    );

    // Build sync key with workflow filename (Option 1)
    String syncKey = JOB_NAME + "_" + repoName + "_" + workflowFileName;
    Optional<SyncStatus> syncStatus = syncStatusRepository.findById(syncKey);

    // If workflow changed, ignore existing sync status to force full sync
    LocalDateTime lastRun = (!workflowChanged && syncStatus.isPresent())
        ? syncStatus.get().getLastSuccessfulRun()
        : null;

    if (lastRun == null) {
        log.info("Starting full sync for {}/{} with workflow '{}'",
                 owner, repoName, workflowFileName);
    }

    // Rest of the method remains the same...
    List<GitHubWorkflowRunDto> workflowRuns = gitHubClient.getWorkflowRuns(
        owner, repoName, workflowFileName, lastRun
    );

    // ... (rest of existing logic)

    // Update sync status with new key format
    updateSyncStatus(repoName, workflowFileName);
}
```

**Modificar `updateSyncStatus`**:
```java
private void updateSyncStatus(String repoName, String workflowFileName) {
    String syncKey = JOB_NAME + "_" + repoName + "_" + workflowFileName;
    SyncStatus status = new SyncStatus(syncKey, LocalDateTime.now());
    syncStatusRepository.save(status);
    log.debug("Updated sync status: {}", syncKey);
}
```

### 4. Actualizar Tests

#### 4.1. DeploymentSyncServiceTest - Nuevos Tests

**Archivo**: `src/test/java/org/grubhart/pucp/tesis/module_collector/service/DeploymentSyncServiceTest.java`

**Tests a agregar**:

```java
@Test
@DisplayName("GIVEN workflow filename changes WHEN syncing THEN it should reset sync and capture all deployments")
void shouldResetSyncWhenWorkflowChanges() {
    // Setup: Initial workflow
    String oldWorkflow = "old-deploy.yml";
    String newWorkflow = "deploy.yml";

    RepositoryConfig repo = createTestRepository();
    repo.setDeploymentWorkflowFileName(oldWorkflow);
    repo.setLastSyncedWorkflowFile(oldWorkflow);

    // Create old sync status
    SyncStatus oldSyncStatus = new SyncStatus(
        "DEPLOYMENT_SYNC_test-repo_" + oldWorkflow,
        LocalDateTime.now().minusDays(1)
    );
    when(syncStatusRepository.findById(anyString())).thenReturn(Optional.of(oldSyncStatus));

    // Change workflow
    repo.setDeploymentWorkflowFileName(newWorkflow);
    when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));

    GitHubWorkflowRunDto deployment = createTestWorkflowRun();
    when(gitHubClient.getWorkflowRuns(eq(OWNER), eq(REPO_NAME), eq(newWorkflow), isNull()))
        .thenReturn(List.of(deployment));

    // Execute
    deploymentSyncService.syncDeployments();

    // Verify: Should update lastSyncedWorkflowFile
    verify(repositoryConfigRepository, atLeastOnce()).save(argThat(r ->
        newWorkflow.equals(r.getLastSyncedWorkflowFile())
    ));

    // Verify: Should query with null lastRun (full sync)
    verify(gitHubClient).getWorkflowRuns(eq(OWNER), eq(REPO_NAME), eq(newWorkflow), isNull());

    // Verify: Should save deployment
    verify(deploymentRepository).saveAll(anyList());
}

@Test
@DisplayName("GIVEN first time sync WHEN syncing THEN it should set lastSyncedWorkflowFile")
void shouldSetLastSyncedWorkflowOnFirstSync() {
    String workflowFile = "deploy.yml";

    RepositoryConfig repo = createTestRepository();
    repo.setDeploymentWorkflowFileName(workflowFile);
    repo.setLastSyncedWorkflowFile(null); // First time

    when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));
    when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
    when(gitHubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(List.of());

    // Execute
    deploymentSyncService.syncDeployments();

    // Verify
    verify(repositoryConfigRepository, atLeastOnce()).save(argThat(r ->
        workflowFile.equals(r.getLastSyncedWorkflowFile())
    ));
}

@Test
@DisplayName("GIVEN same workflow filename WHEN syncing THEN it should use existing sync status")
void shouldUseExistingSyncStatusWhenWorkflowUnchanged() {
    String workflowFile = "deploy.yml";
    LocalDateTime lastSync = LocalDateTime.now().minusHours(1);

    RepositoryConfig repo = createTestRepository();
    repo.setDeploymentWorkflowFileName(workflowFile);
    repo.setLastSyncedWorkflowFile(workflowFile); // Same workflow

    SyncStatus syncStatus = new SyncStatus(
        "DEPLOYMENT_SYNC_test-repo_" + workflowFile,
        lastSync
    );

    when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));
    when(syncStatusRepository.findById(anyString())).thenReturn(Optional.of(syncStatus));
    when(gitHubClient.getWorkflowRuns(any(), any(), any(), eq(lastSync))).thenReturn(List.of());

    // Execute
    deploymentSyncService.syncDeployments();

    // Verify: Should use lastSync timestamp (incremental sync)
    verify(gitHubClient).getWorkflowRuns(eq(OWNER), eq(REPO_NAME), eq(workflowFile), eq(lastSync));

    // Verify: Should NOT update lastSyncedWorkflowFile (no change)
    verify(repositoryConfigRepository, never()).save(any());
}

@Test
@DisplayName("GIVEN new sync key format WHEN updating status THEN it should include workflow filename")
void shouldIncludeWorkflowInSyncKey() {
    String workflowFile = "deploy.yml";

    RepositoryConfig repo = createTestRepository();
    repo.setDeploymentWorkflowFileName(workflowFile);
    repo.setLastSyncedWorkflowFile(workflowFile);

    when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));
    when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
    when(gitHubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(List.of());

    // Execute
    deploymentSyncService.syncDeployments();

    // Verify: Should save with new key format
    verify(syncStatusRepository).save(argThat(status ->
        status.getJobName().equals("DEPLOYMENT_SYNC_test-repo_" + workflowFile)
    ));
}
```

#### 4.2. Actualizar Tests Existentes

**Tests que necesitan ajuste** (cambio en sync key format):
- Cualquier test que verifique el nombre de `SyncStatus`
- Actualizar de `"DEPLOYMENT_SYNC_{repo}"` a `"DEPLOYMENT_SYNC_{repo}_{workflow}"`

### 5. Testing Manual

#### 5.1. Escenario 1: Workflow incorrecto → corrección
1. Configurar repo con workflow `"wrong.yml"`
2. Ejecutar sync (no encuentra deployments)
3. Cambiar a `"deploy.yml"` (correcto)
4. Ejecutar sync
5. ✅ Verificar: Captura deployments históricos

#### 5.2. Escenario 2: Primer sync
1. Agregar nuevo repo con workflow `"deploy.yml"`
2. Ejecutar sync
3. ✅ Verificar: `lastSyncedWorkflowFile` se guarda
4. ✅ Verificar: Captura deployments

#### 5.3. Escenario 3: Sin cambios
1. Repo con workflow `"deploy.yml"` ya sincronizado
2. Ejecutar sync nuevamente
3. ✅ Verificar: Sync incremental (desde último timestamp)
4. ✅ Verificar: `lastSyncedWorkflowFile` no cambia

## 📊 Beneficios de la Solución

### Opción 1 (SyncStatus key con workflow):
- ✅ Cada workflow tiene estado independiente
- ✅ Permite múltiples workflows por repo en el futuro
- ✅ Cambio de workflow = automáticamente inicia desde cero

### Opción 3 (Detección de cambio):
- ✅ Log explícito cuando cambia configuración
- ✅ Tracking del último workflow sincronizado
- ✅ Datos auditables en la BD
- ✅ Previene confusión del usuario

### Combinadas:
- ✅ Robustez: Doble capa de protección
- ✅ Claridad: Logs informativos
- ✅ Mantenibilidad: Estado claro en BD
- ✅ Sin pérdida de datos: Captura históricos siempre

## 🚨 Consideraciones

### Breaking Changes:
- **SyncStatus keys existentes** usaban formato antiguo
- **Efecto**: Primer sync después del deploy será completo (no incremental)
- **Impacto**: Aceptable - garantiza consistencia

### Performance:
- **Primer sync completo** puede traer muchos deployments
- **Mitigación**: Ya existe lógica de deduplicación por `githubId`

### Rollback:
- Si necesitas rollback, los nuevos campos serán `null`
- El código antiguo seguirá funcionando (ignora campos nuevos)

## ✅ Checklist de Implementación

- [ ] Agregar `lastSyncedWorkflowFile` a `RepositoryConfig`
- [ ] Implementar `detectAndHandleWorkflowChange()`
- [ ] Modificar `syncDeploymentsForRepository()` para incluir workflow en sync key
- [ ] Modificar `updateSyncStatus()` para aceptar workflow filename
- [ ] Escribir test: workflow change triggers full sync
- [ ] Escribir test: first sync sets lastSyncedWorkflowFile
- [ ] Escribir test: unchanged workflow uses incremental sync
- [ ] Escribir test: sync key includes workflow filename
- [ ] Actualizar tests existentes afectados por cambio de sync key
- [ ] Testing manual: escenario de corrección de error
- [ ] Testing manual: escenario de primer sync
- [ ] Testing manual: escenario sin cambios
- [ ] Code review
- [ ] Commit con mensaje: `TESIS-192: ...`
- [ ] Push a rama de trabajo
- [ ] Merge a rama base

## 📝 Commits Planeados

1. `TESIS-192: add lastSyncedWorkflowFile field to RepositoryConfig entity`
2. `TESIS-192: implement workflow change detection in DeploymentSyncService`
3. `TESIS-192: update sync key format to include workflow filename`
4. `TESIS-192: add unit tests for workflow change detection`
5. `TESIS-192: update existing tests for new sync key format`
6. `TESIS-192: add integration test for complete workflow change scenario`

---

**Estimación**: 2-3 horas
**Prioridad**: Alta
**Risk**: Bajo (mejora robustez, no rompe funcionalidad existente)
