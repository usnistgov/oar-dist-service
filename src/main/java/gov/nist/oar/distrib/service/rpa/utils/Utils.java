package gov.nist.oar.distrib.service.rpa.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.service.rpa.HttpURLConnectionFactory;
import gov.nist.oar.distrib.service.rpa.JWTHelper;
import gov.nist.oar.distrib.service.rpa.exceptions.InternalServerErrorException;
import gov.nist.oar.distrib.service.rpa.exceptions.InvalidRequestException;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility methods for common functionality.
 */
public class Utils {
    private final static Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    /**
     * Builds a URL from the given components.
     *
     * @param baseUrl     the base URL
     * @param path        the path component of the URL
     * @param queryParams a map of query parameter names and values, or null if there are no query parameters
     * @return the string representation of the URL
     * @throws URISyntaxException if the resulting URI is not properly formatted
     */
    public static String buildUrl(String baseUrl, String path, Map<String, String> queryParams) throws URISyntaxException {
        // Check if the baseUrl is null or empty
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty.");
        }

        // Create a new URIBuilder with the instance URL and path
        URIBuilder uriBuilder = new URIBuilder(baseUrl);

        // Check if the path is null or empty
        if (path != null && !path.isEmpty()) {
            uriBuilder.setPath(path);
        }

        // Add any query parameters specified in the queryParams map
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                uriBuilder.setParameter(entry.getKey(), entry.getValue());
            }
        }

        // Build the URI and return the string representation of the URL
        return uriBuilder.build().toString();
    }


    /**
     * Sends an HTTP request to the specified URL with the given arguments and returns the response as an instance of
     * the given response type.
     *
     * @param <T>               The type of the expected response.
     * @param connectionFactory
     * @param url               The URL to send the request to.
     * @param method            The HTTP request method to use (e.g. GET, POST, PUT, DELETE, etc.).
     * @param payload           The payload to send with the request (can be null if no payload is required).
     * @param responseType      The class representing the expected response type.
     * @return The response as an instance of the specified response type.
     * @throws InvalidRequestException      If the request is invalid (e.g. missing required parameters).
     * @throws InternalServerErrorException If the server returns an error response that is not covered by
     *                                      any of the above exceptions.
     */
    @SuppressWarnings("unused")
    public static <T> T sendHttpRequest(HttpURLConnectionFactory connectionFactory, String url, String method,
                                        String payload, Class<T> responseType)
            throws InvalidRequestException, InternalServerErrorException {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = connectionFactory.createHttpURLConnection(requestUrl);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization",
                    "Bearer " + JWTHelper.getInstance().getToken().getAccessToken());

            if (payload != null) {
                connection.setDoOutput(true);
                OutputStream os = connection.getOutputStream();
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();
            }

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }

                    return new ObjectMapper().readValue(response.toString(), responseType);
                }
            } else {
                // Parse error body to include in debug and exception messages
                String errorBody = readErrorStream(connection.getErrorStream());
                if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                    LOGGER.debug("Invalid request: " + connection.getResponseMessage() + ", details: " + errorBody);
                    throw new InvalidRequestException("Invalid request: " + connection.getResponseMessage() + ", " +
                            "details: " + errorBody);
                } else {
                    LOGGER.debug("Error response from service: " + connection.getResponseMessage() + ", details: " + errorBody);
                    throw new InternalServerErrorException("Error response from service: " +
                            connection.getResponseMessage() + ", details: " + errorBody);
                }
            }
        } catch (MalformedURLException e) {
            throw new InternalServerErrorException("Invalid URL: " + e.getMessage());
        } catch (IOException e) {
            throw new InternalServerErrorException("Error sending request: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Reads the error stream from an HTTP connection and returns the response body as a string.
     *
     * @param inputErrorStream the input stream from the connection's error stream
     * @return the response body as a string
     * @throws IOException if there was an error reading from the stream
     */
    public static String readErrorStream(InputStream inputErrorStream) throws IOException {
        if (inputErrorStream != null) {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputErrorStream))) {
                StringBuilder errorMessage = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    errorMessage.append(line);
                }
                return errorMessage.toString();
            }
        }
        return null;
    }
}

