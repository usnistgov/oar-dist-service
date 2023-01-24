package gov.nist.oar.distrib.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
})
public class RPADataCachingControllerTest {

    Logger logger = LoggerFactory.getLogger(RPADataCachingControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }

    @Test
    public void testCacheDataset() {
        String dsid = "849E1CC6FBE2C4C7E053B3570681FE987034";
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/restricted/" + dsid,
                HttpMethod.PUT, req, String.class);

        String randomId = resp.getBody();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(randomId.contains("https://data.nist.gov/pdr/datacart/restricted_datacart?id="));
        assertEquals(
                randomId.length(),
                "https://data.nist.gov/pdr/datacart/restricted_datacart?id=".length() + 20
        ); // random id len = 20
    }

    @Test
    public void testRetrieveMetadata() {
        String dsid = "849E1CC6FBE2C4C7E053B3570681FE987034";

        // first we cache dataset
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/restricted/" + dsid,
                HttpMethod.PUT, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        //get randomId
        String randomId = resp.getBody();

        // use randomId to fetch metadata
        req = new HttpEntity<String>(null, headers);
        ParameterizedTypeReference<Map<String, Object>> responseType =
                new ParameterizedTypeReference<Map<String,Object>>() {};
        ResponseEntity<Map<String, Object>> response = websvc.exchange(getBaseURL() + "/ds/restricted/" + randomId,
                HttpMethod.GET, req, responseType);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> metadata = response.getBody();
        assertEquals(metadata.size(),3 );
    }
}