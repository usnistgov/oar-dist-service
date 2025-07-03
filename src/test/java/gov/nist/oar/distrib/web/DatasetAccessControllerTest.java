/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 */
package gov.nist.oar.distrib.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.service.NerdmDownloadService;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "server.servlet.context-path=/od",
        "distrib.bagstore.mode=local",
        "distrib.bagstore.location=${basedir}/src/test/resources",
        "distrib.baseurl=http://localhost/oar-distrb-service",
        "logging.path=${basedir}/target/surefire-reports",
        "cloud.aws.region=us-east-1",
        "file.base-dir=${basedir}/src/test/resources/datasets",
// "logging.level.org.springframework.web=DEBUG"
// "logging.level.gov.nist.oar.distrib=DEBUG"
})
public class DatasetAccessControllerTest {

    Logger logger = LoggerFactory.getLogger(DatasetAccessControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    private String getBaseURL() {
        return "http://localhost:" + port + "/od";
    }

    @MockBean
    private NerdmDownloadService nermService;

    private JsonNode mockNerdm(String dsid) throws IOException {
        String json = Files.readString(Paths.get("src/test/resources/datasets/" + dsid + "/nerdm.json"));
        return new ObjectMapper().readTree(json);
}

    @Test
    public void testDescribeAIPs() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "[{ name:mds1491.mbag0_2-0.zip, contentLength:9841 }," +
                "{ name:mds1491.1_1_0.mbag0_4-1.zip, contentLength:14077 }]";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testDescribeAIPsBadID() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/goober/_aip",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/goober/_aip\"," +
                "status:404,message:\"Resource ID not found\",method:GET}",
                resp.getBody(), true);
    }

    @Test
    public void testDescribeAIPsEvil() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() +
                "/ds/%3Cscript%3Egoober%3C%2Fscript%3E/_aip",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().contains("script>"));
    }

    @Test
    public void testDescribeAIPsEvil2() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() +
                "/ds/%3Cscript%3Egoober%3Cscript%3E/_aip",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().contains("script>"));
    }

    @Test
    public void testListAIPVersions() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "[\"1.1.0\", \"0\"]";
        JSONAssert.assertEquals(expect, resp.getBody(), false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testListAIPVersionsBadID() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/goober/_aip/_v",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/goober/_aip/_v\"," +
                "status:404,message:\"Resource ID not found\",method:GET}",
                resp.getBody(), true);
    }

    @Test
    public void testDescribeAIPsForVersion() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v/0",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "[{ name:mds1491.mbag0_2-0.zip, contentLength:9841 }],";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));

        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v/1.1.0",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        expect = "[{ name:mds1491.1_1_0.mbag0_4-1.zip, contentLength:14077 }]";

        got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));

    }

    @Test
    public void testDescribeHeadAIPForVersion() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v/0/_head",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "{ name:mds1491.mbag0_2-0.zip, contentLength:9841 }";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));

        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v/1.1.0/_head",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        expect = "{ name:mds1491.1_1_0.mbag0_4-1.zip, contentLength:14077 }";

        got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));

    }

    @Test
    public void testDescribeAIPsForVersionBadIn() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/goober/_aip/_v/0",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/goober/_aip/_v/0\"," +
                "status:404,message:\"Resource ID not found\",method:GET}",
                resp.getBody(), true);

        req = new HttpEntity<String>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_v/12.32",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/mds1491/_aip/_v/12.32\"," +
                "status:404,message:\"Requested version of resource not found\",method:GET}",
                resp.getBody(), true);
    }

    @Test
    public void testDdescribeLatestHeadAIP() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_aip/_head",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String expect = "{ name:mds1491.1_1_0.mbag0_4-1.zip, contentLength:14077 }";

        String got = resp.getBody();
        JSONAssert.assertEquals(expect, got, false);
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
    }

    @Test
    public void testDescribeHeadAIPForVersionBadInp() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/goober/_aip/_head",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/goober/_aip/_head\"," +
                "status:404,message:\"Resource ID not found\",method:GET}",
                resp.getBody(), true);

    }

    @Test
    public void testDownloadFile() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testDownloadFileViaARK() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/ark:/1212/mds1491/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testRcloneIndexOrInvalidDatasetID() throws JSONException {

        try {
                when(nermService.fetchNerdm("mds1491")).thenReturn(mockNerdm("mds1491"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        HttpEntity<String> req = new HttpEntity<>(null, headers);

        // Case 1: Invalid dataset
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491.json",
                HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        JSONAssert.assertEquals("{requestURL:\"/od/ds/mds1491.json\"," +
                "status:404,message:\"Resource ID not found\",method:GET}",
                resp.getBody(), true);

        // Case 2: Valid dataset directory request (rclone-style index)
        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/", HttpMethod.GET, req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("text/html"));
        assertTrue(resp.getBody().contains("Rclone Index Page")); // or any part of your HTML
    }

    @Test
    public void testDownloadFileBadInp() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/goober/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        JSONAssert.assertEquals("{requestURL:\"/od/ds/goober/trial1.json\"," +
                "status:404,message:\"Resource ID not found\",method:GET}",
                resp.getBody(), true);

        req = new HttpEntity<String>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/goober/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        JSONAssert.assertEquals("{requestURL:\"/od/ds/mds1491/goober/trial1.json\"," +
                "status:404,message:\"File not found in requested dataset\",method:GET}",
                resp.getBody(), true);

    }

    @Test
    public void testDownloadFileMalInp() throws JSONException {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/goob er/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        JSONAssert.assertEquals("{requestURL:\"/od/ds/goob%20er/trial1.json\"," +
                "status:400,message:\"Malformed input\",method:GET}",
                resp.getBody(), true);

        /*
         * Apparently the client or server is normalizing this input URL before our
         * controller
         * gets it.
         *
         * req = new HttpEntity<String>(null, headers);
         * resp = websvc.exchange(getBaseURL() +
         * "/ds/mds1491/trial3/../trial3/trial3a.json",
         * HttpMethod.GET, req, String.class);
         * 
         * assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
         * assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith(
         * "application/json"));
         * JSONAssert.assertEquals(
         * "{requestURL:\"/od/ds/mds1491/trial3/../trial3/trial3a.json\"," +
         * "status:400,message:\"Malformed input\",method:GET}",
         * resp.getBody(), true);
         */

        req = new HttpEntity<String>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/.trial3a.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        JSONAssert.assertEquals("{requestURL:\"/od/ds/mds1491/.trial3a.json\"," +
                "status:400,message:\"Malformed input\",method:GET}",
                resp.getBody(), true);
    }

    @Test
    public void testDownloadFileFromVersion() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_v/0/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());

        req = new HttpEntity<String>(null, headers);
        resp = websvc.exchange(getBaseURL() + "/ds/mds1491/_v/1.1.0/trial1.json",
                HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertEquals(69, resp.getBody().length());
    }

    @Test
    public void testDownloadFileInfo() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/trial1.json",
                HttpMethod.HEAD, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertNull(resp.getBody());
    }

    @Test
    public void testDownloadFileInfoViaARK() {
        HttpEntity<String> req = new HttpEntity<String>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/ark:/8888/mds1491/trial1.json",
                HttpMethod.HEAD, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertNull(resp.getBody());
    }

    @Test
    public void testDownloadFileInfoViaBadARK() {
        // Set the Accept header to request JSON
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<String> req = new HttpEntity<>(null, headers);

        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/ark:/mds1491/goob/trial1.json",
                HttpMethod.HEAD, req, String.class);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));
        assertNull(resp.getBody());
    }

    @Test
    public void testBadDatasetIDPattern() {
        assertTrue(DatasetAccessController.baddsid.matcher("goober gurn").find());
        assertTrue(DatasetAccessController.baddsid.matcher("goober\tgurn").find());
        assertFalse(DatasetAccessController.baddsid.matcher("goober").find());
    }

    @Test
    public void testBadFilePathPattern() {
        assertTrue(DatasetAccessController.badpath.matcher(".goobergurn").find());
        assertTrue(DatasetAccessController.badpath.matcher("goober/../../../gurn").find());
        assertFalse(DatasetAccessController.badpath.matcher("goober..gurn").find());
    }

    @Test
    public void testRcloneIndexRootFolder() throws Exception  {
        try {
                when(nermService.fetchNerdm("mds1491")).thenReturn(mockNerdm("mds1491"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(
                getBaseURL() + "/ds/mds1491/", HttpMethod.GET, req, String.class);

        System.out.println("Content-Type: " + resp.getHeaders().getFirst("Content-Type"));
        System.out.println(resp.getBody());
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").contains("text/html"));
        assertTrue(resp.getBody().contains("<title>")); // basic sanity check for HTML
        assertTrue(resp.getBody().contains("trial1.json")); // known file in dataset
    }

    @Test
    public void testRcloneIndexSubfolder() {

        try {
                when(nermService.fetchNerdm("mds1491")).thenReturn(mockNerdm("mds1491"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/subfolder/", HttpMethod.GET, req,
                String.class);

        System.out.println("Content-Type: " + resp.getHeaders().getFirst("Content-Type"));
        System.out.println(resp.getBody());
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("text/html"));
        assertTrue(resp.getBody().contains("trial1.json"));
    }

    @Test
    public void testRcloneIndexRestrictedComponentsAreSkipped() {

        try {
                when(nermService.fetchNerdm("mds1491")).thenReturn(mockNerdm("mds1491"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/mds1491/", HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("text/html"));
        assertFalse(resp.getBody().contains("restricted-file.json")); // Adjust to real restricted component
    }

    @Test
    public void testRcloneIndexEmptyFolder() {
        try {
                when(nermService.fetchNerdm("emptydataset")).thenReturn(mockNerdm("emptydataset"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(getBaseURL() + "/ds/emptydataset/", HttpMethod.GET, req,
                String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("text/html"));
        assertTrue(resp.getBody().contains("Rclone Index Page")); // Template should still render even if empty
        assertFalse(resp.getBody().contains("<li>")); // No files should be listed
    }

    @Test
    public void testRcloneIndexEmptyFolderMissingNerdm() throws IOException, JSONException {
        when(nermService.fetchNerdm("emptydataset")).thenReturn(null);

        HttpEntity<String> req = new HttpEntity<>(null, headers);
        ResponseEntity<String> resp = websvc.exchange(
                        getBaseURL() + "/ds/emptydataset/", HttpMethod.GET, req, String.class);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertTrue(resp.getHeaders().getFirst("Content-Type").startsWith("application/json"));

        JSONAssert.assertEquals(
                        "{status:404,message:'Resource ID not found'}",
                        resp.getBody(),
                        false);
    }

    /*
     * see comment for DatasetAccessController.testErrorHandling()
     *
     * @Test
     * public void testIllegalStateExceptionHndling() {
     * HttpEntity<String> req = new HttpEntity<String>(null, headers);
     * ResponseEntity<String> resp = websvc.exchange(getBaseURL() +
     * "/ds/_error/goob",
     * HttpMethod.GET, req, String.class);
     * assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
     * }
     */
}
