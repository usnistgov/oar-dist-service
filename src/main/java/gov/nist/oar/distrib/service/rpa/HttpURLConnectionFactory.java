package gov.nist.oar.distrib.service.rpa;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The ConnectionFactory interface provides a method for creating instances
 * of {@link HttpURLConnection} based on a given {@link URL}.
 * <p>
 * This interface allows for the decoupling of the process of creating
 * HttpURLConnection instances from the classes that use them, making it
 * easier to test and modify the connection creation process if needed.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * ConnectionFactory connectionFactory = url -> (HttpURLConnection) url.openConnection();
 *
 * URL requestUrl = new URL("https://example.com");
 * HttpURLConnection connection = connectionFactory.createHttpURLConnection(requestUrl);
 * }
 * </pre>
 * <p>
 * To use a custom ConnectionFactory implementation for testing or other purposes,
 * create a class that implements the ConnectionFactory interface and provide the
 * custom implementation in the createHttpURLConnection method.
 */
public interface HttpURLConnectionFactory {
    /**
     * Creates a new {@link HttpURLConnection} instance for the specified {@link URL}.
     *
     * @param url the URL for which to create an HttpURLConnection instance
     * @return a new HttpURLConnection instance for the specified URL
     * @throws IOException if an I/O exception occurs while opening the connection
     */
    HttpURLConnection createHttpURLConnection(URL url) throws IOException;
}
