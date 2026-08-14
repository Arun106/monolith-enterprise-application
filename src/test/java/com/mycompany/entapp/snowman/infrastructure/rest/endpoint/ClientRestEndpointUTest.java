package com.mycompany.entapp.snowman.infrastructure.rest.endpoint;

import com.mycompany.entapp.snowman.domain.exception.SnowmanException;
import com.mycompany.entapp.snowman.domain.model.Client;
import com.mycompany.entapp.snowman.domain.service.ClientService;
import com.mycompany.entapp.snowman.infrastructure.rest.mappers.ClientResourceMapper;
import com.mycompany.entapp.snowman.infrastructure.rest.resources.ClientResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ClientResourceMapper.class)
public class ClientRestEndpointUTest {

    @Mock
    private ClientService clientService;

    @InjectMocks
    private ClientRestEndpoint endpoint = new ClientRestEndpoint();

    @Test
    public void getClientReturnsMappedResource() {
        Integer clientId = 7;
        Client client = new Client();
        ClientResource resource = new ClientResource();
        PowerMockito.mockStatic(ClientResourceMapper.class);
        Mockito.when(clientService.getClient(clientId)).thenReturn(client);
        PowerMockito.when(ClientResourceMapper.mapToClientResource(client)).thenReturn(resource);

        ResponseEntity<ClientResource> response = endpoint.getClientInfo(clientId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(resource, response.getBody());
    }

    @Test
    public void createClientDelegatesToService() throws Exception {
        ClientResource resource = new ClientResource();
        Client client = new Client();
        PowerMockito.mockStatic(ClientResourceMapper.class);
        PowerMockito.when(ClientResourceMapper.mapToClient(resource)).thenReturn(client);

        endpoint.createClientInfo(resource);

        Mockito.verify(clientService).createClient(client);
    }

    @Test(expected = RuntimeException.class)
    public void createClientWrapsBusinessFailure() throws Exception {
        ClientResource resource = new ClientResource();
        Client client = new Client();
        PowerMockito.mockStatic(ClientResourceMapper.class);
        PowerMockito.when(ClientResourceMapper.mapToClient(resource)).thenReturn(client);
        Mockito.doThrow(new SnowmanException("create failed"))
            .when(clientService).createClient(client);

        endpoint.createClientInfo(resource);
    }

    @Test
    public void updateClientDelegatesToService() throws Exception {
        ClientResource resource = new ClientResource();
        Client client = new Client();
        PowerMockito.mockStatic(ClientResourceMapper.class);
        PowerMockito.when(ClientResourceMapper.mapToClient(resource)).thenReturn(client);

        endpoint.updateClientInfo(resource);

        Mockito.verify(clientService).updateClient(client);
    }

    @Test
    public void deleteClientDelegatesToService() throws Exception {
        endpoint.deleteClientInfo(9);

        Mockito.verify(clientService).deleteClient(9);
    }
}
