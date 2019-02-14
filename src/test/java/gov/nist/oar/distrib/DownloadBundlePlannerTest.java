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
package gov.nist.oar.distrib;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.datapackage.DownloadBundlePlanner;
import gov.nist.oar.distrib.web.objects.BundleDownloadPlan;
import gov.nist.oar.distrib.web.objects.BundleRequest;
import gov.nist.oar.distrib.web.objects.FileRequest;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DownloadBundlePlannerTest {

    @Test
    public void getBundleDownloadPlanTest() throws JsonParseException, JsonMappingException, IOException {
	FileRequest[] inputfileList = new FileRequest[2];
	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	BundleRequest bFL = new BundleRequest("testdownload", inputfileList);
	DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 200000, 3,
		"s3.amazonaws.com|project-open-data.cio.gov", "testdownload");
	BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
	assertEquals(bundlePlan.getPostEachTo(), "_bundle");
	assertEquals(bundlePlan.getStatus(), "complete");
	assertEquals(bundlePlan.getBundleNameFilePathUrl().length, 1);
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length, 2);

    }

    @Test
    public void getBundleDownloadPlan2Test() throws JsonParseException, JsonMappingException, IOException {
	FileRequest[] inputfileList = new FileRequest[3];
	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1894/open-data.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";
	String val3 = "{\"filePath\":\"/1894/contract.pdf\",\"downloadUrl\":\"https://www.nist.gov/sites/default/files/documents/2018/12/26/letter_for_contractors_12.26.2018.pdf/\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	FileRequest testval3 = mapper.readValue(val3, FileRequest.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	inputfileList[2] = testval3;
	BundleRequest bFL = new BundleRequest("testdownload", inputfileList);
	DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 2000000, 3, "s3.amazonaws.com|nist.gov",
		"testdownload");
	BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
	assertEquals(bundlePlan.getPostEachTo(), "_bundle");
	assertEquals(bundlePlan.getStatus(), "warnings");
	assertEquals(bundlePlan.getBundleNameFilePathUrl().length, 1);
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getBundleName(), "testdownload-1.zip");
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length, 2);

    }
    
    @Test
    public void getBundleDownloadPlan3Test() throws JsonParseException, JsonMappingException, IOException {
	FileRequest[] inputfileList = new FileRequest[3];
	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1894/open-data.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";
	String val3 = "{\"filePath\":\"/1894/contract.pdf\",\"downloadUrl\":\"https://www.nist.gov/sites/default/files/documents/2018/12/26/letter_for_contractors_12.26.2018.pdf/\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	FileRequest testval3 = mapper.readValue(val3, FileRequest.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	inputfileList[2] = testval3;
	BundleRequest bFL = new BundleRequest("testdownload", inputfileList);
	DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 200, 2, "s3.amazonaws.com|nist.gov",
		"testdownload");
	BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
	System.out.println("Bundle Plan:"+ bundlePlan.getBundleNameFilePathUrl()[0].getBundleName());
	BundleRequest[] test = bundlePlan.getBundleNameFilePathUrl();
	assertEquals(bundlePlan.getPostEachTo(), "_bundle");
	assertEquals(bundlePlan.getStatus(), "warnings");
	assertEquals(bundlePlan.getBundleNameFilePathUrl().length, 2);
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getBundleName(), "testdownload-1.zip");
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length, 1);

    }
}
