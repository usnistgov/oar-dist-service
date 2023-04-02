package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.InternalServerErrorException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.web.RPAConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for the {@link JWTHelper} class.
 * These tests verify the behavior of the {@link JWTHelper} class and its methods.
 * <p>
 * The {@link JWTHelper} class is responsible for creating and sending JWT tokens for Salesforce authentication.
 * <p>
 * These unit tests mock the dependencies of the {@link JWTHelper} class, such as the {@link KeyRetriever}
 * and {@link RPAConfiguration} {@link CloseableHttpClient} objects, to isolate the class and test its behavior
 * in a controlled environment.
 * <p>
 * Note: This class relies on the Mockito testing framework and the JUnit 4 test runner to perform the unit tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class JWTHelperTest {

    @Mock
    private KeyRetriever keyRetriever;

    @Mock
    private RPAConfiguration mockConfig;

    @Mock
    private HttpURLConnection mockConnection;

    @Mock
    private HttpURLConnectionFactory mockConnectionFactory;

    private JWTHelper jwtHelper;

    private Key testPrivateKey;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        jwtHelper = JWTHelper.getInstance();
        jwtHelper.setKeyRetriever(keyRetriever);
        jwtHelper.setConfig(mockConfig);
        jwtHelper.setHttpURLConnectionFactory(mockConnectionFactory);
        try {
            testPrivateKey = generateFakePrivateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            mockCreateAssertion();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // generate a fake private key for testing
    private Key generateFakePrivateKey() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.genKeyPair();
        Key privateKey = kp.getPrivate();
        return privateKey;
    }

    // setup mocks to create a test assertion inside the JWTHelper object
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


    /**
     * Test for method {@link JWTHelper#getToken()}.
     * Verify that the correct request properties are set on the HttpURLConnection
     * and that the response InputStream is correctly parsed and returned as a JWTToken.
     *
     * @throws IOException                  if there is an issue sending the request or parsing the response
     * @throws InternalServerErrorException if there is an issue building the request URL or handling the response
     */
    @Test
    public void testGetToken_success() throws Exception, InternalServerErrorException {
        mockCreateAssertion();
        // Create a testUrl
        String expectedUrl = createTestUrl(createTestAssertion());
        when(mockConnectionFactory.createHttpURLConnection(new URL(expectedUrl))).thenReturn(mockConnection);
        // Set expected dummy response
        String expectedResponseData = "{\"access_token\":\"DUMMY_TOKEN\",\"instance_url\":\"https://instanceUrl.com\"}";
        // Create an input stream with some dummy data
        InputStream inputStream = new ByteArrayInputStream(expectedResponseData.getBytes());
        // When getInputStream() is called on the mockConnection, return the dummy input stream
        when(mockConnection.getInputStream()).thenReturn(inputStream);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        // Call the sendTokenRequest method
        JWTToken actualToken = jwtHelper.getToken();

        ObjectMapper mapper = new ObjectMapper();
        JWTToken expectedToken = mapper.readValue(expectedResponseData, JWTToken.class);
        assertEquals(expectedToken.getAccessToken(), actualToken.getAccessToken());
        assertEquals(expectedToken.getInstanceUrl(), actualToken.getInstanceUrl());

        // Verify that the request method and headers were set correctly
        verify(mockConnection).setRequestMethod("POST");
        verify(mockConnection).setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetToken_badRequest() throws Exception {
        mockCreateAssertion();
        String testUrl = createTestUrl(createTestAssertion());
        when(mockConnectionFactory.createHttpURLConnection(new URL(testUrl))).thenReturn(mockConnection);
        when(mockConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        when(mockConnection.getResponseMessage()).thenReturn("Bad Request");

        // Verify InternalServerErrorException exception is thrown
        try {
            // Call method to test
            JWTToken token = jwtHelper.getToken();
            fail("Expected InternalServerErrorException to be thrown");
        } catch (InternalServerErrorException e) {
            // Verify exception message is correct
            assertEquals("Access token request is invalid: Bad Request", e.getMessage());
        }
        // Verify that the connection was closed
        verify(mockConnection).disconnect();
    }

    @Test
    public void testGetToken_failure_malformedURL() throws Exception {
        mockCreateAssertion();
        // Introduce bug in URL here, we inject the JWTHelper with an invalid Salesforce Instance Url
        when(mockConfig.getSalesforceInstanceUrl()).thenReturn("https://login.salesforce.com:BUG_HERE");

        // Verify InternalServerErrorException exception is thrown
        try {
            // Call method to test
            JWTToken token = jwtHelper.getToken();
            fail("Expected InternalServerErrorException to be thrown");
        } catch (InternalServerErrorException e) {
            // Verify exception message is correct
            assertThat(e.getMessage(), containsString("Invalid URL: "));
        }
    }
}
