package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_collector.DeploymentSyncTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final DeploymentSyncTrigger deploymentSyncTrigger;

    public AdminController(DeploymentSyncTrigger deploymentSyncTrigger) {
        this.deploymentSyncTrigger = deploymentSyncTrigger;
    }

    @PostMapping("/sync/deployments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerDeploymentSync() {
        logger.info("Manual deployment synchronization triggered via API");
        try {
            deploymentSyncTrigger.syncDeployments();
            return ResponseEntity.ok(Map.of("message", "Deployment synchronization triggered successfully."));
        } catch (Exception e) {
            logger.error("Manual deployment synchronization failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Deployment synchronization failed: " + e.getMessage()));
        }
    }
}
