package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class JWTHelperTest {

    @Mock
    private KeyRetriever mockKeyRetriever;

    @Mock
    private RPAConfiguration mockConfig;

    @Mock
    private RestTemplate mockRestTemplate;

    @Mock
    private ResponseEntity<JWTToken> mockResponseEntity;

    @Mock
    private JWTToken mockJWTToken;

    @InjectMocks
    private JWTHelper jwtHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        jwtHelper.setKeyRetriever(mockKeyRetriever);
        jwtHelper.setConfig(mockConfig);
        jwtHelper.setRestTemplate(mockRestTemplate);
    }

    @Test
    public void testGetToken() throws Exception {
        // Arrange
        String assertion = "dummy assertion";
        when(mockConfig.getSalesforceInstanceUrl()).thenReturn("https://test.my.salesforce.com");
        when(mockConfig.getSalesforceJwt()).thenReturn(mockSalesforceJwt);
        when(mockConfig.getSalesforceJwt().getGrantType()).thenReturn("dummy grant type");
        when(mockKeyRetriever.getKey(mockConfig)).thenReturn(mockKey);
        when(mockRestTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(JWTToken.class)))
                .thenReturn(mockResponseEntity);
        when(mockResponseEntity.getBody()).thenReturn(mockJWTToken);

        // Act
        JWTToken token = jwtHelper.getToken();

        // Assert
        verify(mockRestTemplate).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(JWTToken.class));
        verify(mockResponseEntity).getBody();
        assertEquals(mockJWTToken, token);
    }
}
