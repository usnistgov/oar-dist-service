package gov.nist.oar.distrib.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;

public class NermDownloadService {

    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public NermDownloadService(String baseUrl, CloseableHttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
    }

    /**
     * Fetch the NERDm document associated with the given dataset identifier.
     *
     * @param dsid the dataset identifier
     * @return the NERDm document as a {@link JsonNode}
     * @throws IOException if the request fails or the response is not a valid JSON document
     */
    public JsonNode fetchNerdm(String dsid) throws IOException {
        String url = baseUrl + "/od/id/" + dsid;
        HttpGet get = new HttpGet(url);

        return httpClient.execute(get, response -> {
            int status = response.getCode();
            if (status != 200) {
                throw new IOException("NERDm fetch failed: HTTP " + status);
            }
            String body = EntityUtils.toString(response.getEntity());
            return mapper.readTree(body);
        });
    }

}
