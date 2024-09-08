package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.InternalServerErrorException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.web.RPAConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Enable Mockito integration with JUnit 5
public class JWTHelperTest {

    @Mock
    private KeyRetriever keyRetriever;

    @Mock
    private RPAConfiguration mockConfig;

    @Mock
    private HttpURLConnection mockConnection;

    @Mock
    private HttpURLConnectionFactory mockConnectionFactory;

    @InjectMocks
    private JWTHelper jwtHelper; // Inject the mocks into the JWTHelper instance

    private Key testPrivateKey;

    @BeforeEach
    public void setup() {
        try {
            testPrivateKey = generateFakePrivateKey();
            mockCreateAssertion();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Generate a fake private key for testing
    private Key generateFakePrivateKey() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.genKeyPair();
        return kp.getPrivate();
    }

    // Setup mocks to create a test assertion inside the JWTHelper object
    private void mockCreateAssertion() throws NoSuchAlgorithmException {
        when(mockConfig.getSalesforceJwt()).thenReturn(mock(RPAConfiguration.SalesforceJwt.class));
        when(mockConfig.getSalesforceJwt().getExpirationInMinutes()).thenReturn(3);
        when(mockConfig.getSalesforceJwt().getClientId()).thenReturn("test_clientId");
        when(mockConfig.getSalesforceJwt().getAudience()).thenReturn("test_audience");
        when(mockConfig.getSalesforceJwt().getSubject()).thenReturn("test_subject");
        when(keyRetriever.getKey(mockConfig)).thenReturn(testPrivateKey);
    }

    private String createTestAssertion() {
        LocalDateTime localDateTime = LocalDateTime.now().plusMinutes(mockConfig.getSalesforceJwt().getExpirationInMinutes());
        return Jwts.builder()
                .setIssuer(mockConfig.getSalesforceJwt().getClientId())
                .setAudience(mockConfig.getSalesforceJwt().getAudience())
                .setSubject(mockConfig.getSalesforceJwt().getSubject())
                .setExpiration(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(SignatureAlgorithm.RS256, testPrivateKey)
                .compact();
    }

    // This function is to create a URL similar to the one used when sending the actual request
    private String createTestUrl(String assertion) {
        when(mockConfig.getSalesforceInstanceUrl()).thenReturn("https://login.salesforce.com");
        when(mockConfig.getSalesforceJwt().getGrantType()).thenReturn("urn:ietf:params:oauth:grant-type:jwt-bearer");
        String url = null;
        try {
            url = new URIBuilder(mockConfig.getSalesforceInstanceUrl())
                    .setPath("/services/oauth2/token")
                    .setParameter("grant_type", mockConfig.getSalesforceJwt().getGrantType())
                    .setParameter("assertion", assertion)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new InternalServerErrorException("Malformed URL");
        }
        return url;
    }

    @Test
    public void testGetToken_success() throws Exception {
        mockCreateAssertion();
        String expectedUrl = createTestUrl(createTestAssertion());
        when(mockConnectionFactory.createHttpURLConnection(new URI(expectedUrl).toURL()))
                .thenReturn(mockConnection);

        String expectedResponseData = "{\"access_token\":\"DUMMY_TOKEN\",\"instance_url\":\"https://instanceUrl.com\"}";
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        JWTToken actualToken = jwtHelper.getToken();

        ObjectMapper mapper = new ObjectMapper();
        JWTToken expectedToken = mapper.readValue(expectedResponseData, JWTToken.class);
        assertEquals(expectedToken.getAccessToken(), actualToken.getAccessToken());
        assertEquals(expectedToken.getInstanceUrl(), actualToken.getInstanceUrl());

        verify(mockConnection).setRequestMethod("POST");
        verify(mockConnection).setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetToken_badRequest() throws Exception {
        mockCreateAssertion();
        String testUrl = createTestUrl(createTestAssertion());
        when(mockConnectionFactory.createHttpURLConnection(new URI(testUrl).toURL()))
                .thenReturn(mockConnection);

        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        Exception exception = assertThrows(InternalServerErrorException.class, () -> jwtHelper.getToken());
        assertEquals("Access token request is invalid: Bad Request", exception.getMessage());

        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetToken_failure_malformedURL() throws Exception {
        mockCreateAssertion();
        when(mockConfig.getSalesforceInstanceUrl()).thenReturn("https://login.salesforce.com:BUG_HERE");

        Exception exception = assertThrows(InternalServerErrorException.class, () -> jwtHelper.getToken());
        assertTrue(exception.getMessage().contains("Invalid URL:"));
    }
}
