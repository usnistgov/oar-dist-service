package gov.nist.oar.distrib.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NerdmDownloadService {

    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    // Time To Live for each cached entry - 5 minutes
    private final Duration cacheTTL = Duration.ofMinutes(5);

    private static final Logger logger = LoggerFactory.getLogger(NerdmDownloadService.class);

    // FIFO cache with max size 2.
    // This uses a LinkedHashMap that holds at most 2 entries.
    // - The false flag means insertion-order (not access-order), so it's true
    // FIFO.
    // - The 0.75f is a required load factor value, but it's ignored here since there is no resize
    // - When a third entry is added, the oldest one (first inserted) will be
    // automatically removed.
    private final Map<String, CachedValue> nerdmCache = new LinkedHashMap<>(2, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedValue> eldest) {
            return size() > 2;
        }
    };

    public NerdmDownloadService(String baseUrl, CloseableHttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
    }

    public NerdmDownloadService(String baseUrl) {
        this(baseUrl, HttpClients.createDefault());
    }

    /**
     * Fetch the NERDm record for the dataset using dsid from the NERDm service.
     * If the record is already cached and the age of the
     * cached record is less than the cache TTL, that cached value is returned.  If the
     * cached record is old or does not exist, the service is queried and the result is
     * cached (with a successful or failed result) and returned.
     *
     * @param dsid  the dataset identifier
     * @return  the JSON representation of the NERDm record of the dataset with the given
     *          identifier
     * @throws IOException  if a network error occurs or if the NERDm service fails to
     *                      return a record for the given dataset
     */
    public JsonNode fetchNerdm(String dsid) throws IOException {
        Instant now = Instant.now();
        CachedValue cached = nerdmCache.get(dsid);

        if (cached != null && Duration.between(cached.timestamp, now).compareTo(cacheTTL) < 0) {
            logger.debug("Returning cached NERDm record for dsid={}", dsid);
            if (cached.data.isPresent())
                return cached.data.get();
            else
                throw new IOException("Cached NERDm fetch previously failed for dsid=" + dsid);
        }

        String url = baseUrl.endsWith("/") ? baseUrl + dsid : baseUrl + "/" + dsid;
        logger.info("Fetching NERDm from URL: {}", url);

        HttpGet get = new HttpGet(url);

        // Fetch the NERDm record from resolver
        try {
            JsonNode result = httpClient.execute(get, response -> {
                int status = response.getCode();
                logger.debug("Received status {} for dsid={}", status, dsid);

                if (status == 404)
                    throw new NerdmNotFoundException(dsid);
                else if (status != 200)
                    throw new IOException("NERDm fetch failed: HTTP " + status);

                String body = EntityUtils.toString(response.getEntity());
                return mapper.readTree(body);
            });

            nerdmCache.put(dsid, new CachedValue(Optional.of(result), now));
            return result;

        } catch (IOException e) {
            logger.error("Error fetching NERDm for dsid={}: {}", dsid, e.getMessage(), e);
            nerdmCache.put(dsid, new CachedValue(Optional.empty(), now));
            throw e;
        }
    }


    private static class CachedValue {
        final Optional<JsonNode> data;
        final Instant timestamp;

        CachedValue(Optional<JsonNode> data, Instant timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}