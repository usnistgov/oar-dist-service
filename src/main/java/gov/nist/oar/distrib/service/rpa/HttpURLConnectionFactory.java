package gov.nist.oar.distrib.service.rpa;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The HttpURLConnectionFactory interface provides a method for creating instances
 * of {@link HttpURLConnection} based on a given {@link URL}.
 * <pre></pre>
 * This interface allows decoupling the creation of HttpURLConnection instances from the classes that use them, making it
 * easier to test and modify the connection creation process if needed.
 * <p>
 * This design is important when unit testing classes that use HttpURLConnection; we can replace
 * the HttpURLConnectionFactory implementation with one that returns mocked connections.
 *
 * <pre></pre>
 * Example usage:
 * <pre>
 * {@code
 * HttpURLConnectionFactory connectionFactory = new HttpURLConnectionFactory() {
 *     @Override
 *     public HttpURLConnection createHttpURLConnection(URL url) throws IOException {
 *          return (HttpURLConnection) url.openConnection();
 *     }
 * };
 * URL requestUrl = new URL("https://example.com");
 * HttpURLConnection connection = connectionFactory.createHttpURLConnection(requestUrl);
 * }
 * </pre>
 * <p>
 * To use a custom HttpURLConnectionFactory implementation for testing or other purposes,
 * create a class that implements the HttpURLConnectionFactory interface and provide the
 * custom implementation in the createHttpURLConnection method.
 */
public interface HttpURLConnectionFactory {
    /**
     * Creates a new {@link HttpURLConnection} instance for the given {@link URL}.
     *
     * @param url the URL for which to create an HttpURLConnection instance
     * @return a new HttpURLConnection instance for the given URL
     * @throws IOException if an error occurs while opening the connection
     */
    HttpURLConnection createHttpURLConnection(URL url) throws IOException;
}
