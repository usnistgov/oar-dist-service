package gov.nist.oar.distrib.service.rpa;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.client.utils.URIBuilder;

/**
 * Utility methods for common functionality.
 */
public class Utils {

    /**
     * Builds a URL from the given components.
     *
     * @param baseUrl the base URL
     * @param path the path component of the URL
     * @param queryParams a map of query parameter names and values, or null if there are no query parameters
     * @return the string representation of the URL
     * @throws URISyntaxException if the resulting URI is not properly formatted
     */
    public static String buildUrl(String baseUrl, String path, Map<String, String> queryParams) throws URISyntaxException {
        // Create a new URIBuilder with the instance URL and path
        URIBuilder uriBuilder = new URIBuilder(baseUrl).setPath(path);

        // Add any query parameters specified in the queryParams map
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                uriBuilder.setParameter(entry.getKey(), entry.getValue());
            }
        }

        // Build the URI and return the string representation of the URL
        return uriBuilder.build().toString();
    }
}

