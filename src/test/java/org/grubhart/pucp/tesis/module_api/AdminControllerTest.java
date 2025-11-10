package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_collector.DeploymentSyncTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private DeploymentSyncTrigger deploymentSyncTrigger;

    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(deploymentSyncTrigger);
    }

    @Test
    @DisplayName("triggerDeploymentSync debe llamar al servicio y devolver 200 OK")
    void triggerDeploymentSync_shouldCallServiceAndReturnOk() {
        // Act
        ResponseEntity<Map<String, String>> response = adminController.triggerDeploymentSync();

        // Assert
        verify(deploymentSyncTrigger).syncDeployments();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Deployment synchronization triggered successfully.", response.getBody().get("message"));
    }

    @Test
    @DisplayName("triggerDeploymentSync debe devolver 500 si el servicio falla")
    void triggerDeploymentSync_whenServiceFails_shouldReturnInternalServerError() {
        // Arrange
        String errorMessage = "Database connection failed";
        doThrow(new RuntimeException(errorMessage)).when(deploymentSyncTrigger).syncDeployments();

        // Act
        ResponseEntity<Map<String, String>> response = adminController.triggerDeploymentSync();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Deployment synchronization failed: " + errorMessage, response.getBody().get("error"));
    }
}
