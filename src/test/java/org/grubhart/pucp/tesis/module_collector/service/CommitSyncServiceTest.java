package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitParent;
import org.grubhart.pucp.tesis.module_domain.CommitParentRepository;
import org.grubhart.pucp.tesis.module_domain.CommitRepository;
import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.SyncStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitSyncServiceTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitParentRepository commitParentRepository;

    @Mock
    private SyncStatusRepository syncStatusRepository;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @Mock
    private GithubCommitCollector githubCommitCollector;

    @InjectMocks
    private CommitSyncService commitSyncService;

    private static final String VALID_URL = "https://github.com/owner/repo";
    private static final String OWNER = "owner";
    private static final String REPO = "repo";


    @Test
    @DisplayName("Dado que no hay repositorios configurados, el servicio no debe hacer nada")
    void syncCommits_whenNoRepositoriesConfigured_shouldDoNothing() {
        // GIVEN: El repositorio de configuración devuelve una lista vacía.
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se ejecuta el servicio de sincronización.
        commitSyncService.syncCommits();

        // THEN: No se debe intentar obtener commits ni guardar ningún estado.
        verify(githubCommitCollector, never()).getCommits(anyString(), anyString(), any());
        verify(syncStatusRepository, never()).save(any());
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado un repositorio con URL mal formada, el servicio debe saltarlo")
    void syncCommits_whenRepositoryUrlIsMalformed_shouldSkipIt() {
        // GIVEN: El repositorio de configuración devuelve una URL inválida.
        RepositoryConfig malformedConfig = new RepositoryConfig("url-invalida-sin-separadores");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(malformedConfig));

        // WHEN: Se ejecuta el servicio de sincronización.
        commitSyncService.syncCommits();

        // THEN: No se debe intentar obtener commits ni guardar ningún estado.
        verify(githubCommitCollector, never()).getCommits(anyString(), anyString(), any());
        verify(syncStatusRepository, never()).save(any());
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado un repositorio con nombre de repo nulo o en blanco, el servicio debe saltarlo")
    void syncCommits_whenRepoNameIsBlank_shouldSkipSync() {
        // GIVEN: Una configuración con una URL que resultará en un repoName nulo.
        RepositoryConfig invalidConfig = new RepositoryConfig("https://github.com/owner/"); // URL con repo en blanco
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(invalidConfig));

        // WHEN: Se ejecuta el servicio de sincronización.
        commitSyncService.syncCommits();

        // THEN: No se debe intentar obtener commits ni guardar ningún estado.
        verify(githubCommitCollector, never()).getCommits(anyString(), anyString(), any());
        verify(syncStatusRepository, never()).save(any());
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado un repositorio configurado, debe sincronizar los commits correctamente")
    void syncCommits_whenRepositoryIsConfigured_shouldSyncCommits() {
        // GIVEN: Una configuración de repositorio válida.
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        // Simulamos que no hay un estado de sincronización previo.
        when(syncStatusRepository.findById("COMMIT_SYNC_owner/repo")).thenReturn(java.util.Optional.empty());

        // Simulamos que la API de GitHub no devuelve nuevos commits.
        when(githubCommitCollector.getCommits(eq(OWNER), eq(REPO), any())).thenReturn(Collections.emptyList());

        // WHEN: Se ejecuta el servicio de sincronización.
        commitSyncService.syncCommits();

        // THEN: Se debe verificar que se intentó obtener los commits y que se guardó el nuevo estado de sincronización.
        verify(githubCommitCollector, times(1)).getCommits(eq(OWNER), eq(REPO), any());
        verify(syncStatusRepository, times(1)).save(any());
        // Verificamos que no se intentó guardar commits, ya que la lista estaba vacía.
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado un repositorio con nuevos commits, debe guardarlos en la BD")
    void syncCommits_whenNewCommitsAreFound_shouldSaveThem() {
        // GIVEN: Una configuración válida y la API de GitHub devuelve un nuevo commit.
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        GithubCommitDto newCommitDto = new GithubCommitDto();
        newCommitDto.setSha("new-commit-sha");

        when(githubCommitCollector.getCommits(eq(OWNER), eq(REPO), any())).thenReturn(List.of(newCommitDto));

        // Simulamos que este commit NO existe en la BD.
        when(commitRepository.existsById("new-commit-sha")).thenReturn(false);

        // WHEN
        commitSyncService.syncCommits();

        // THEN: Verificamos que se intentó guardar el nuevo commit.
        ArgumentCaptor<List<Commit>> captor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository, times(1)).saveAll(captor.capture());

        List<Commit> savedCommits = captor.getValue();
        assertThat(savedCommits).hasSize(1);
        assertThat(savedCommits.get(0).getSha()).isEqualTo("new-commit-sha");

        // Verificamos que el estado de sincronización se actualizó.
        verify(syncStatusRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Dado un error al obtener commits, el servicio debe registrarlo y no fallar")
    void syncCommits_whenCollectorThrowsException_shouldHandleErrorGracefully() {
        // GIVEN: Una configuración válida, pero el colector de commits lanza una excepción.
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        when(githubCommitCollector.getCommits(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("API de GitHub no disponible"));

        // WHEN
        commitSyncService.syncCommits();

        // THEN: Verificamos que no se intentó guardar nada y que la sincronización no se actualizó.
        verify(commitRepository, never()).saveAll(any());
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dado que la API devuelve una mezcla de commits nuevos y existentes, solo debe guardar los nuevos")
    void syncCommits_whenApiReturnsMixedCommits_shouldOnlySaveNewOnes() {
        // GIVEN
        // Una configuración de repositorio válida.
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        // La API devuelve dos commits: uno que ya existe y uno nuevo.
        GithubCommitDto existingCommitDto = new GithubCommitDto();
        existingCommitDto.setSha("sha-existente");

        GithubCommitDto newCommitDto = new GithubCommitDto();
        newCommitDto.setSha("sha-nuevo");

        when(githubCommitCollector.getCommits(anyString(), anyString(), any()))
                .thenReturn(Arrays.asList(existingCommitDto, newCommitDto));

        // Simulamos la lógica de la base de datos.
        when(commitRepository.existsById("sha-existente")).thenReturn(true);
        when(commitRepository.existsById("sha-nuevo")).thenReturn(false);

        // WHEN
        commitSyncService.syncCommits();

        // THEN
        // Verificamos que se intentó guardar una lista que contiene SOLO el commit nuevo.
        ArgumentCaptor<List<Commit>> captor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository, times(1)).saveAll(captor.capture());

        List<Commit> savedCommits = captor.getValue();
        assertThat(savedCommits).hasSize(1);
        assertThat(savedCommits.get(0).getSha()).isEqualTo("sha-nuevo");
    }

    @Test
    @DisplayName("syncCommits debe saltar la sincronización si la configuración del repositorio no tiene 'owner'")
    void syncCommits_shouldSkipSync_whenRepositoryConfigHasNoOwner() {
        // 1. Arrange
        // Creamos una configuración de repositorio donde 'owner' es nulo.
        // Una URL sin una ruta clara resultará en un owner nulo.
        RepositoryConfig malformedConfig = new RepositoryConfig("https://github.com");

        // Configuramos el mock para que devuelva esta configuración inválida.
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.singletonList(malformedConfig));

        // 2. Act
        // Ejecutamos el método principal que queremos probar.
        commitSyncService.syncCommits();

        // 3. Assert
        // Verificamos que NUNCA se intentó obtener commits, ya que la configuración era inválida.
        verify(githubCommitCollector, never()).getCommits(any(), any(), any());

        // Verificamos que NUNCA se intentó guardar nada en los repositorios.
        verify(commitRepository, never()).saveAll(any());
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dado un nuevo commit cuyo padre ya existe, solo debe guardar el nuevo commit")
    void syncCommits_whenParentCommitAlreadyExists_shouldOnlySaveNewCommit() {
        // GIVEN
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        String newCommitSha = "new-commit-sha";
        String existingParentSha = "parent-commit-sha";

        // Usar instancias reales de DTOs para consistencia y claridad.
        GithubCommitDto.ParentDto parentDto = new GithubCommitDto.ParentDto();
        parentDto.setSha(existingParentSha);

        GithubCommitDto newCommitDto = new GithubCommitDto();
        newCommitDto.setSha(newCommitSha);
        newCommitDto.setParents(List.of(parentDto));

        when(githubCommitCollector.getCommits(eq(OWNER), eq(REPO), any())).thenReturn(List.of(newCommitDto));

        // Configuración completa del mock del repositorio para el flujo lógico.
        when(commitRepository.existsById(newCommitSha)).thenReturn(false); // El commit es nuevo.
        when(commitRepository.findById(existingParentSha)).thenReturn(Optional.of(new Commit())); // El padre ya existe.
        when(commitRepository.findById(newCommitSha)).thenReturn(Optional.of(new Commit(newCommitDto))); // El commit hijo se encuentra después de ser guardado.

        // WHEN
        commitSyncService.syncCommits();

        // THEN
        // Se debe guardar una lista que contiene solo el nuevo commit.
        ArgumentCaptor<List<Commit>> captor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository, times(1)).saveAll(captor.capture());
        List<Commit> savedCommits = captor.getValue();
        assertThat(savedCommits).hasSize(1);
        assertThat(savedCommits.get(0).getSha()).isEqualTo(newCommitSha);

        // Se debe buscar al padre para establecer la relación, lo que prueba que la lógica continuó.
        verify(commitRepository, times(1)).findById(existingParentSha);
    }

    @Test
    @DisplayName("Dado un commit cuyo padre no se encuentra en la BD, debe omitir la relación")
    void syncCommits_whenParentCommitIsNotFound_shouldSkipRelationship() {
        // GIVEN
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        String childSha = "child-commit-sha";
        String nonExistentParentSha = "non-existent-parent-sha";

        GithubCommitDto.ParentDto parentDto = new GithubCommitDto.ParentDto();
        parentDto.setSha(nonExistentParentSha);

        GithubCommitDto childDto = new GithubCommitDto();
        childDto.setSha(childSha);
        childDto.setParents(List.of(parentDto));

        when(githubCommitCollector.getCommits(eq(OWNER), eq(REPO), any())).thenReturn(List.of(childDto));

        // El commit hijo es nuevo y se guarda.
        when(commitRepository.existsById(childSha)).thenReturn(false);
        // El commit hijo se encuentra después de ser guardado.
        when(commitRepository.findById(childSha)).thenReturn(Optional.of(new Commit(childDto)));
        // El commit padre NO se encuentra en la BD.
        when(commitRepository.findById(nonExistentParentSha)).thenReturn(Optional.empty());

        // WHEN
        commitSyncService.syncCommits();

        // THEN
        // Verificamos que se buscó al padre.
        verify(commitRepository).findById(nonExistentParentSha);

        // Verificamos que se guardó el commit hijo.
        ArgumentCaptor<List<Commit>> commitCaptor = ArgumentCaptor.forClass(List.class);
        verify(commitRepository).saveAll(commitCaptor.capture());
        assertThat(commitCaptor.getValue()).hasSize(1);
        assertThat(commitCaptor.getValue().get(0).getSha()).isEqualTo(childSha);

        // La aserción clave: verificamos que NUNCA se intentó guardar ninguna relación de parentesco.
        verify(commitParentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado que solo se encuentra una nueva relación de parentesco, solo debe guardar la relación")
    void syncCommits_whenOnlyNewParentRelationshipIsFound_shouldSaveOnlyRelationship() {
        // GIVEN
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        String childSha = "child-sha";
        String parentSha = "parent-sha";

        // DTOs para simular la respuesta de la API
        GithubCommitDto.ParentDto parentDto = new GithubCommitDto.ParentDto();
        parentDto.setSha(parentSha);
        GithubCommitDto childDto = new GithubCommitDto();
        childDto.setSha(childSha);
        childDto.setParents(List.of(parentDto));

        when(githubCommitCollector.getCommits(eq(OWNER), eq(REPO), any())).thenReturn(List.of(childDto));

        // Simulamos que el commit hijo YA EXISTE y no necesita ser guardado.
        when(commitRepository.existsById(childSha)).thenReturn(true);

        // Simulamos que tanto el hijo como el padre existen en la BD y pueden ser recuperados.
        Commit childCommit = new Commit(childDto);
        Commit parentCommit = new Commit(parentSha, null, null, null);
        when(commitRepository.findById(childSha)).thenReturn(Optional.of(childCommit));
        when(commitRepository.findById(parentSha)).thenReturn(Optional.of(parentCommit));

        // Punto Clave: La relación de parentesco NO EXISTE todavía.
        when(commitParentRepository.existsByCommitShaAndParentSha(childSha, parentSha)).thenReturn(false);

        // WHEN
        commitSyncService.syncCommits();

        // THEN
        // Verificamos que NO se guardó ningún commit, ya que todos existían.
        verify(commitRepository, never()).saveAll(any());

        // Verificamos que SÍ se guardó la nueva relación de parentesco.
        ArgumentCaptor<List<CommitParent>> parentCaptor = ArgumentCaptor.forClass(List.class);
        verify(commitParentRepository, times(1)).saveAll(parentCaptor.capture());

        List<CommitParent> savedParents = parentCaptor.getValue();
        assertThat(savedParents).hasSize(1);
        CommitParent savedParent = savedParents.get(0);
        assertThat(savedParent.getCommit()).isNotNull();
        assertThat(savedParent.getCommit().getSha()).isEqualTo(childSha);
        assertThat(savedParent.getParent()).isNotNull();
        assertThat(savedParent.getParent().getSha()).isEqualTo(parentSha);
    }

    @Test
    @DisplayName("Dado que una relación de parentesco ya existe, no debe hacer nada")
    void syncCommits_whenParentRelationshipAlreadyExists_shouldDoNothing() {
        // GIVEN
        RepositoryConfig validConfig = new RepositoryConfig(VALID_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        String childSha = "child-sha";
        String parentSha = "parent-sha";

        // DTOs para simular la respuesta de la API
        GithubCommitDto.ParentDto parentDto = new GithubCommitDto.ParentDto();
        parentDto.setSha(parentSha);
        GithubCommitDto childDto = new GithubCommitDto();
        childDto.setSha(childSha);
        childDto.setParents(List.of(parentDto));

        when(githubCommitCollector.getCommits(eq(OWNER), eq(REPO), any())).thenReturn(List.of(childDto));

        // Simulamos que el commit hijo YA EXISTE.
        when(commitRepository.existsById(childSha)).thenReturn(true);

        // Simulamos que tanto el hijo como el padre existen en la BD.
        when(commitRepository.findById(childSha)).thenReturn(Optional.of(new Commit(childDto)));
        when(commitRepository.findById(parentSha)).thenReturn(Optional.of(new Commit(parentSha, null, null, null)));

        // Punto Clave: La relación de parentesco YA EXISTE.
        when(commitParentRepository.existsByCommitShaAndParentSha(childSha, parentSha)).thenReturn(true);

        // WHEN
        commitSyncService.syncCommits();

        // THEN
        // Verificamos que NO se guardó ningún commit.
        verify(commitRepository, never()).saveAll(any());
        // La aserción clave: verificamos que NUNCA se intentó guardar ninguna relación de parentesco.
        verify(commitParentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado múltiples repositorios, debe sincronizar commits para todos")
    void shouldSyncForAllConfiguredRepositories() {
        // GIVEN
        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/owner1/repo1");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/owner2/repo2");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo1, repo2));

        // WHEN
        commitSyncService.syncCommits();

        // THEN
        verify(githubCommitCollector, times(1)).getCommits(eq("owner1"), eq("repo1"), any());
        verify(githubCommitCollector, times(1)).getCommits(eq("owner2"), eq("repo2"), any());
        verify(syncStatusRepository, times(2)).save(any());
    }
}
