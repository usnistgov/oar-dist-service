package gov.nist.oar.distrib.service.rpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.exceptions.InternalServerErrorException;
import gov.nist.oar.distrib.service.rpa.model.JWTToken;
import gov.nist.oar.distrib.web.RPAConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * The JWTHelper class provides a helper method for generating a JSON Web Token (JWT) and sending a token request
 * to a Salesforce authentication server.
 * <p>
 * This class is implemented using the singleton pattern to ensure that only one instance exists throughout the application.
 * <p>
 * The class uses the Salesforce OAuth2 token endpoint to request a JWT token, which is signed using an RSA private key.
 * <p>
 * To use this class, you must first set the {@link KeyRetriever}, {@link RPAConfiguration},
 * and {@link HttpURLConnectionFactory} properties using the corresponding setter methods.
 * You can then call the getToken() method to get a JWT token.
 * <p>
 * Example usage:
 * <pre>{@code
 * JWTHelper jwtHelper = JWTHelper.getInstance();
 * jwtHelper.setKeyRetriever(keyRetriever);
 * jwtHelper.setConfig(config);
 * JWTToken token = jwtHelper.getToken();
 * }</pre>
 */
public class JWTHelper {

    private final static Logger LOGGER = LoggerFactory.getLogger(JWTHelper.class);
    // Singleton instance of the class
    private static JWTHelper instance;
    private KeyRetriever keyRetriever;
    private RPAConfiguration config;
    /**
     * The purpose of the HttpURLConnectionFactory interface is to abstract the process of creating HttpURLConnection
     * instances from the classes that use them.
     * By using a ConnectionFactory, we decouple the creation of HttpURLConnection instances from the logic that
     * sends requests and processes responses. This design will help with unit testing.
     */
    private HttpURLConnectionFactory connectionFactory;

    // Private constructor to prevent direct instantiation of the class
    private JWTHelper() {

        this.connectionFactory = new HttpURLConnectionFactory() {
            @Override
            public HttpURLConnection createHttpURLConnection(URL url) throws IOException {
                return (HttpURLConnection) url.openConnection();
            }
        };
    }

    public static synchronized JWTHelper getInstance() {
        if (instance == null) {
            instance = new JWTHelper();
        }
        return instance;
    }

    // Setter methods for the KeyRetriever, RPAConfiguration, and HttpURLConnectionFactory properties

    public void setKeyRetriever(KeyRetriever keyRetriever) {
        this.keyRetriever = keyRetriever;
    }

    public void setConfig(RPAConfiguration config) {
        this.config = config;
    }

    public void setHttpURLConnectionFactory(HttpURLConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    // Get ad JWT token
    public JWTToken getToken() throws InternalServerErrorException {
        return sendTokenRequest(createAssertion());
    }

    // Create the JST assertion.
    private String createAssertion() {
        LocalDateTime localDateTime = LocalDateTime.now().plusMinutes(config.getSalesforceJwt().getExpirationInMinutes());
        return Jwts.builder()
                .setIssuer(config.getSalesforceJwt().getClientId())
                .setAudience(config.getSalesforceJwt().getAudience())
                .setSubject(config.getSalesforceJwt().getSubject())
                .setExpiration(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(SignatureAlgorithm.RS256, keyRetriever.getKey(config))
                .compact();
    }

    // Send a token request as per rfc7523
    private JWTToken sendTokenRequest(String assertion) throws InternalServerErrorException {
        String url = null;
        try {
            url = new URIBuilder(config.getSalesforceInstanceUrl())
                    .setPath("/services/oauth2/token")
                    .setParameter("grant_type", config.getSalesforceJwt().getGrantType())
                    .setParameter("assertion", assertion)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new InternalServerErrorException("Error occurred while building oauth2 token URI. " +
                    "The URI may contain invalid characters or is not properly formatted.");
        }

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = connectionFactory.createHttpURLConnection(requestUrl);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }

                    // Unmarshall response
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readValue(response.toString(), JWTToken.class);
                }
            } else {
                LOGGER.debug("Access token request is invalid: " + connection.getResponseMessage());
                throw new InternalServerErrorException("Access token request is invalid: " + connection.getResponseMessage());
            }
        } catch (MalformedURLException e) {
            LOGGER.debug("Invalid URL: " + e.getMessage());
            throw new InternalServerErrorException("Invalid URL: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.debug("Error sending POST request: " + e.getMessage());
            throw new InternalServerErrorException("error sending POST request: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
