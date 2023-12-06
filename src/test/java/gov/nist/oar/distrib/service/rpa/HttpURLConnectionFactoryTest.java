package gov.nist.oar.distrib.service.rpa;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for the {@link HttpURLConnectionFactory} interface and its implementations.
 */
public class HttpURLConnectionFactoryTest {

    private static final URL TEST_URL;

    static {
        try {
            TEST_URL = new URL("https://example.com");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

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
        URL url = new URL("https://example.com");
        HttpURLConnection connection = factory.createHttpURLConnection(url);
        Assert.assertNotNull(connection);
    }
}
