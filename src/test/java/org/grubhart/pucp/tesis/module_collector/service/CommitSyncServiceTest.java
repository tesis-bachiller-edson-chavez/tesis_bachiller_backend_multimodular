package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.Commit;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitSyncServiceTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private SyncStatusRepository syncStatusRepository;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @Mock
    private GithubCommitCollector githubCommitCollector;

    @InjectMocks
    private CommitSyncService commitSyncService;

    @Test
    @DisplayName("Dado que no hay repositorios configurados, el servicio no debe hacer nada")
    void syncCommits_whenNoRepositoriesConfigured_shouldDoNothing() {
        // GIVEN: El repositorio de configuración devuelve una lista vacía.
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se ejecuta el servicio de sincronización.
        commitSyncService.syncCommits();

        // THEN: No se debe intentar obtener commits ni guardar ningún estado.
        // Verificamos que no hubo interacciones con los otros componentes.
        verify(githubCommitCollector, never()).getCommits(anyString(), anyString(), any());
        verify(syncStatusRepository, never()).save(any());
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado un repositorio con URL mal formada, el servicio debe registrar un error y no continuar")
    void syncCommits_whenRepositoryUrlIsMalformed_shouldLogErrorAndStop() {
        // GIVEN: El repositorio de configuración devuelve una URL inválida.
        RepositoryConfig malformedConfig = new RepositoryConfig("url-invalida"); // URL sin '/'
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(malformedConfig));

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
        RepositoryConfig validConfig = new RepositoryConfig("https://github.com/owner/repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        // Simulamos que no hay un estado de sincronización previo.
        when(syncStatusRepository.findById("COMMIT_SYNC")).thenReturn(java.util.Optional.empty());

        // Simulamos que la API de GitHub no devuelve nuevos commits.
        when(githubCommitCollector.getCommits(eq("owner"), eq("repo"), any())).thenReturn(Collections.emptyList());

        // WHEN: Se ejecuta el servicio de sincronización.
        commitSyncService.syncCommits();

        // THEN: Se debe verificar que se intentó obtener los commits y que se guardó el nuevo estado de sincronización.
        verify(githubCommitCollector, times(1)).getCommits(eq("owner"), eq("repo"), any());
        verify(syncStatusRepository, times(1)).save(any());
        // Verificamos que no se intentó guardar commits, ya que la lista estaba vacía.
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Dado un repositorio con nuevos commits, debe guardarlos en la BD")
    void syncCommits_whenNewCommitsAreFound_shouldSaveThem() {
        // GIVEN: Una configuración válida y la API de GitHub devuelve un nuevo commit.
        RepositoryConfig validConfig = new RepositoryConfig("https://github.com/owner/repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        GithubCommitDto newCommitDto = new GithubCommitDto();
        newCommitDto.setSha("new-commit-sha");
        // (Podríamos poblar más campos si el mapper fuera más complejo)

        when(githubCommitCollector.getCommits(eq("owner"), eq("repo"), any())).thenReturn(List.of(newCommitDto));

        // Simulamos que este commit NO existe en la BD.
        when(commitRepository.existsById("new-commit-sha")).thenReturn(false);

        // WHEN
        commitSyncService.syncCommits();

        // THEN: Verificamos que se intentó guardar el nuevo commit.
        // Usamos un ArgumentCaptor para inspeccionar lo que se pasó a saveAll.
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
        RepositoryConfig validConfig = new RepositoryConfig("https://github.com/owner/repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        when(githubCommitCollector.getCommits(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("API de GitHub no disponible"));

        // WHEN
        commitSyncService.syncCommits();

        // THEN: Verificamos que no se intentó guardar nada y que la sincronización no se actualizó.
        verify(commitRepository, never()).saveAll(any());
        verify(syncStatusRepository, never()).save(any());
    }
}