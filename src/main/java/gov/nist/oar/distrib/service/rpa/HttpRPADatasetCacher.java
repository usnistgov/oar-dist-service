package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

/**
 * An implementation of the {@link RPADatasetCacher} interface that caches datasets using an HTTP request.
 */
public class HttpRPADatasetCacher implements RPADatasetCacher {
    private final static Logger LOGGER = LoggerFactory.getLogger(HttpRPADatasetCacher.class);
    private final static String RPA_CACHE_PATH = "/od/ds/rpa/cache/";
    private RPAConfiguration rpaConfiguration;
    private HttpClient httpClient;

    /**
     * Creates an {@link HttpRPADatasetCacher} object with the given {@link RPAConfiguration}.
     *
     * @param config the {@link RPAConfiguration} object to use for caching datasets
     */
    public HttpRPADatasetCacher(RPAConfiguration config) {
        this.rpaConfiguration = config;
        this.httpClient = HttpClientBuilder.create().build();
    }

    /**
     * Caches the dataset with the given ID.
     *
     * @param datasetId the ID of the dataset to cache
     * @return the randomId generated after successfully caching the dataset
     * @throws RequestProcessingException if an error occurs while caching the dataset
     */
    @Override
    public String cache(String datasetId) {
        String url = buildUrl(this.rpaConfiguration, datasetId);
        LOGGER.debug("Sending 'PUT' request to URL:=" + url);
        return sendHttpRequest(datasetId, url);
    }

    @Override
    public boolean uncache(String randomId) {
        return false;
    }

    /**
     * Builds the URL for the given dataset ID and using the given {@link RPAConfiguration} object.
     *
     * @param rpaConfiguration the {@link RPAConfiguration} used for building the URL
     * @param datasetId        the ID of the dataset to build the URL for
     * @return the URL for the given dataset ID
     * @throws RequestProcessingException if an error occurs while building the URL
     */
    private String buildUrl(RPAConfiguration rpaConfiguration, String datasetId) {
        String url;
        try {
            url = new URIBuilder(this.rpaConfiguration.getPdrCachingUrl())
                    .setPath(RPA_CACHE_PATH + datasetId)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RequestProcessingException("Error building URI: " + e.getMessage());
        }
        return url;
    }

    /**
     * Caches the dataset with the specified ID using Apache {@link org.apache.http.client.HttpClient}.
     *
     * @param datasetId the ID of the dataset to cache
     * @param url       the URL to use for caching the dataset
     * @return the randomId generated after successfully caching the dataset
     * @throws RequestProcessingException if an error occurs while caching the dataset
     */
    private String sendHttpRequest(String datasetId, String url) {
        HttpPut request = new HttpPut(url);
        try {
            HttpResponse response = httpClient.execute(request);
            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode == HttpStatus.SC_OK) {
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(responseEntity.getContent()))) {
                        StringBuilder responseString = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            responseString.append(line);
                        }
                        return responseString.toString();
                    }
                } else {
                    throw new RequestProcessingException("No response entity for caching dataset " + datasetId);
                }
            } else {
                throw new RequestProcessingException("Failed to cache dataset " + datasetId + ": " + response.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
            throw new RequestProcessingException("Failed to cache dataset " + datasetId + ": " + e.getMessage());
        }
    }

    /**
     * Sets the {@link HttpClient} instance for this object.
     * This is mainly used for unit testing purposes, but can also be useful to provide a custom client implementation.
     *
     * @param httpClient the {@link HttpClient} instance to be used
     */
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

}
