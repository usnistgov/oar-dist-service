package gov.nist.oar.distrib.web;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
})
public class RestrictedDataControllerTest {

    Logger logger = LoggerFactory.getLogger(RestrictedDataControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }

    @Test
    public void testGetUrls() {
        String dsid = "849E1CC6FBE2C4C7E053B3570681FE987034";
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/restricted/" + dsid,
                HttpMethod.GET, req, String.class);

//        String expect = "[\"/B3DYZl6Lh4msCe9cMlJt/ngc0055+3.con.gif\"," +
//                "\"/B3DYZl6Lh4msCe9cMlJt/ngc0055+3.con.fits\"," +
//                "\"/B3DYZl6Lh4msCe9cMlJt/ngc0055.HI.gif\"]";
//
//        String got = resp.getBody();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));

    }

}
