/*
 * |-------------------------------------------------
 * | Copyright © 2018 Colin But. All rights reserved.
 * |-------------------------------------------------
 */
package com.mycompany.entapp.snowman.infrastructure.management;

import com.mycompany.entapp.snowman.application.healthcheck.HealthCheck;
import com.mycompany.entapp.snowman.application.healthcheck.HealthStatus;
import com.mycompany.entapp.snowman.infrastructure.management.resource.StatusResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckRestEndpoint {

    @Autowired
    private HealthCheck healthCheck;

    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public ResponseEntity checkStatus() {
        HealthStatus healthStatus = healthCheck.getHealthStatus();

        StatusResource statusResource = new StatusResource();
        statusResource.setStatus(healthStatus.getStatusString());

        if (healthStatus == HealthStatus.UP) {
            return ResponseEntity.ok(statusResource);
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(statusResource);
    }
}
