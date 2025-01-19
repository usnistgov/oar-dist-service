package gov.nist.oar.distrib.service.rpa;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link HttpURLConnectionFactory} interface and its implementations.
 */
public class HttpURLConnectionFactoryTest {

    private static final String TEST_URL = "https://example.com";

    /**
     * An implementation of the {@link HttpURLConnectionFactory} interface that creates {@link HttpURLConnection}
     * objects using the default {@link URL#openConnection()} method.
     */
    public class HttpURLConnectionFactoryImpl implements HttpURLConnectionFactory {
        /**
         * Creates a new {@link HttpURLConnection} instance for the given {@link URL}.
         *
         * @param url the URL for which to create an HttpURLConnection instance
         * @return a new HttpURLConnection instance for the given URL
         * @throws IOException if an error occurs while opening the connection
         */
        @Override
        public HttpURLConnection createHttpURLConnection(URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }
    }

    /**
     * Tests the {@link HttpURLConnectionFactoryImpl#createHttpURLConnection(URL)} method.
     *
     * @throws IOException if an error occurs while opening the connection
     */
    @Test
    public void testCreateHttpURLConnection() throws IOException {
        HttpURLConnectionFactory factory = new HttpURLConnectionFactoryImpl();
        // Test with a valid URL
        URI uri = null;
        try {
            uri = new URI(TEST_URL);
        } catch (URISyntaxException e) {
            fail("URI Syntax Exception: " + e.getMessage());
        }
        HttpURLConnection connection = factory.createHttpURLConnection(uri.toURL());
        assertNotNull(connection, "HttpURLConnection should not be null");
    }
}
