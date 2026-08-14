package com.mycompany.entapp.snowman.infrastructure.management;

import com.mycompany.entapp.snowman.application.cache.ClientCacheService;
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
public class CacheManagementRestEndpointUTest {

    @Mock
    private ClientCacheService clientCacheService;

    @InjectMocks
    private CacheManagementRestEndpoint endpoint = new CacheManagementRestEndpoint();

    @Test
    public void clearCacheDelegatesToServiceAndReturnsOk() {
        ResponseEntity response = endpoint.clearClientCache("clients");

        Mockito.verify(clientCacheService).clearCache();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
