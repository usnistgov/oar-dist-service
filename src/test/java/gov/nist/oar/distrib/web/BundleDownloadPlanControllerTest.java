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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import gov.nist.oar.RequireWebSite;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.FileRequest;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = NISTDistribServiceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "distrib.bagstore.mode=local",
	"distrib.bagstore.location=${basedir}/src/test/resources",
	"distrib.baseurl=http://localhost/od/ds",
        "logging.path=${basedir}/target/surefire-reports",
	"distrib.packaging.maxpackagesize = 2000000",
        "distrib.packaging.maxfilecount = 2",
	"distrib.packaging.allowedurls = nist.gov|s3.amazonaws.com/nist-midas|httpstat.us" })
public class BundleDownloadPlanControllerTest {
    RequireWebSite required = new RequireWebSite("https://s3.amazonaws.com/nist-midas/1894/license.pdf");
    Logger logger = LoggerFactory.getLogger(BundleDownloadPlanControllerTest.class);

    @LocalServerPort
    int port;

    TestRestTemplate websvc = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();

    private String getBaseURL() {
	return "http://localhost:" + port + "/od";
    }

    @Test
    public void testBundlePlanWithWarnings()
	    throws JsonParseException, JsonMappingException, IOException, URISyntaxException, Exception {
        assumeTrue(required.checkSite());
        
	FileRequest[] inputfileList = new FileRequest[2];
	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/projectopen.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	BundleRequest bFL = new BundleRequest("testdownload-3", inputfileList,0,2);
	RequestEntity<BundleRequest> request = RequestEntity.post(new URI(getBaseURL() + "/ds/_bundle_plan"))
		.body(bFL);

	ResponseEntity<String> response = websvc.exchange(request, String.class);

	assertEquals(HttpStatus.OK, response.getStatusCode());
	assertTrue(response.getHeaders().getFirst("Content-Type").startsWith("application/json"));

	ObjectMapper mapperResults = new ObjectMapper();
	String responsetest = response.getBody();
//	System.out.println("Response :"+responsetest);
	BundleDownloadPlan testResponse = mapperResults.readValue(responsetest, BundleDownloadPlan.class);

	assertEquals("warnings", testResponse.getStatus());
	assertEquals("_bundle", testResponse.getPostEachTo());
	assertEquals("/projectopen.pdf", testResponse.getNotIncluded()[0].getFilePath());
	assertEquals("/1894/license.pdf",
		testResponse.getBundleNameFilePathUrl()[0].getIncludeFiles()[0].getFilePath());

    }

    @Test
    public void testBundlePlanWithExceptions()
	    throws JsonParseException, JsonMappingException, IOException, URISyntaxException, Exception {
	FileRequest[] inputfileList = new FileRequest[3];
	String val1 = "{\"filePath\":\"<html>/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1895/license2.pdf</body>\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val3 = "{\"filePath\":\"/1896/license3.pdf<i>TEST Error Here</i>\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	FileRequest testval3 = mapper.readValue(val3, FileRequest.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	inputfileList[2] = testval3;

	BundleRequest bFL = new BundleRequest("testdownload-4", inputfileList,99,2);
	RequestEntity<BundleRequest> request = RequestEntity.post(new URI(getBaseURL() + "/ds/_bundle_plan"))
		.body(bFL);
	System.out.println("request.getStatusCode():" + request + " \n request.getHeaders() :"
			+ request.getHeaders() + "\n request.getBody():" + request.getBody());
	
	ResponseEntity<String> response = websvc.exchange(request, String.class);
	System.out.println("response.getStatusCode():" + response.getStatusCode() + " \n resp.getHeaders() :"
		+ response.getHeaders() + "\n resp.getBody().length():" + response.getBody());

	ObjectMapper mapperResults = new ObjectMapper();
	String responsetest = response.getBody();
	BundleDownloadPlan testResponse = mapperResults.readValue(responsetest, BundleDownloadPlan.class);

	assertEquals("Error", testResponse.getStatus());
	assertEquals(0, testResponse.getNotIncluded().length);
	assertEquals(0, testResponse.getBundleNameFilePathUrl().length);

    }
    
    
    @Test
    public void testBundlePlanWithErrors()
	    throws JsonParseException, JsonMappingException, IOException, URISyntaxException, Exception {
	FileRequest[] ifileList = new FileRequest[2];
	String file1 = "{\"filePath\":\"someid/srd13_Al-001.json\",\"downloadUrl\":\"http://www.nist.gov/srd/srd_data/srd13_Al-001.json\"}";
	String file2 = "{\"filePath\":\"someid/srd13_Al-002.json\",\"downloadUrl\":\"http://www.nist.gov/srd/srd_data/srd13_Al-001.json\"}";
	
		    
	ObjectMapper mapper = new ObjectMapper();
	FileRequest fileRequest1 = mapper.readValue(file1, FileRequest.class);
	FileRequest fileRequest2 = mapper.readValue(file2, FileRequest.class);
	
	
	ifileList[0] = fileRequest1;
	ifileList[1] = fileRequest2;
	
	
	BundleRequest bundleRequest = new BundleRequest("testdownload-5", ifileList,0,2);
	RequestEntity<BundleRequest> newRequest = RequestEntity.post(new URI(getBaseURL() + "/ds/_bundle_plan"))
		.body(bundleRequest);

	ResponseEntity<String> newResponse = websvc.exchange(newRequest, String.class);

	assertEquals(HttpStatus.OK, newResponse.getStatusCode());
	assertTrue(newResponse.getHeaders().getFirst("Content-Type").startsWith("application/json"));

	ObjectMapper mapperResults = new ObjectMapper();
	String respBody = newResponse.getBody();
	BundleDownloadPlan bundleResponse = mapperResults.readValue(respBody, BundleDownloadPlan.class);
	System.out.println(" Response :"+respBody+ "\n Bundle Response"+bundleResponse.getStatus());
	String message = "Files unavailable due to remote server access problem.".trim();
	assertEquals("Error", bundleResponse.getStatus());
	assertEquals("_bundle", bundleResponse.getPostEachTo());
	assertEquals(message,bundleResponse.getMessages()[0].trim());

    }
    
    @Test
    public void testBundlePlanWithNewWarnings()
	    throws JsonParseException, JsonMappingException, IOException, URISyntaxException, Exception {
        assumeTrue(required.checkSite());
        
	FileRequest[] inputfileList = new FileRequest[3];
	String val1 = "{\"filePath\":\"someid/path/file1\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"someid/path/file2\",\"downloadUrl\":\"http://www.nist.gov/srd/srd_data/srd13_Al-002.json\"}";
	String val3 = "{\"filePath\":\"someid/path/file3\",\"downloadUrl\":\"https://www.nist.gov/srdtest/test1.txt\"}";
		    
	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	FileRequest testval3 = mapper.readValue(val3, FileRequest.class);
	
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	inputfileList[2] = testval3;
	
	BundleRequest bFL = new BundleRequest("testdownload-6", inputfileList,0,2);
	RequestEntity<BundleRequest> request = RequestEntity.post(new URI(getBaseURL() + "/ds/_bundle_plan"))
		.body(bFL);

	ResponseEntity<String> response = websvc.exchange(request, String.class);

	assertEquals(HttpStatus.OK, response.getStatusCode());
	assertTrue(response.getHeaders().getFirst("Content-Type").startsWith("application/json"));

	ObjectMapper mapperResults = new ObjectMapper();
	String responsetest = response.getBody();
	BundleDownloadPlan testResponse = mapperResults.readValue(responsetest, BundleDownloadPlan.class);
//	System.out.println("Response :"+responsetest+"\n"+testResponse.getMessages()[0]);
	String message = "Some of the selected data files unavailable, due to remote server access problem.".trim();
	assertEquals("warnings", testResponse.getStatus());
	assertEquals("_bundle", testResponse.getPostEachTo());
	assertEquals(message,testResponse.getMessages()[0].trim());

    }
}
