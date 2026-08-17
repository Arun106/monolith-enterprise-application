package com.mycompany.entapp.snowman.infrastructure.management;

import com.mycompany.entapp.snowman.application.healthcheck.HealthCheck;
import com.mycompany.entapp.snowman.application.healthcheck.HealthStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckRestEndpointUTest {

    @Mock
    private HealthCheck healthCheck;

    @InjectMocks
    private HealthCheckRestEndpoint endpoint = new HealthCheckRestEndpoint();

    @Test
    public void returnsOkWhenDependenciesAreHealthy() {
        Mockito.when(healthCheck.getHealthStatus()).thenReturn(HealthStatus.UP);

        ResponseEntity response = endpoint.checkStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void returnsServiceUnavailableWhenDatabaseIsDown() {
        Mockito.when(healthCheck.getHealthStatus()).thenReturn(HealthStatus.DOWN);

        ResponseEntity response = endpoint.checkStatus();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
