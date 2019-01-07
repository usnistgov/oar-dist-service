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

import gov.nist.oar.distrib.web.objects.BundleDownloadPlan;
import gov.nist.oar.distrib.web.objects.BundleNameFilePathUrl;
import gov.nist.oar.distrib.web.objects.FilePathUrl;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DownloadBundlePlannerTest {

    @Test
    public void getBundleDownloadPlanTest() throws JsonParseException, JsonMappingException, IOException{
	FilePathUrl[] inputfileList = new FilePathUrl[2];
	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";

	ObjectMapper mapper = new ObjectMapper();
	FilePathUrl testval1 = mapper.readValue(val1, FilePathUrl.class);
	FilePathUrl testval2 = mapper.readValue(val2, FilePathUrl.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	BundleNameFilePathUrl bFL = new BundleNameFilePathUrl("testdownload", inputfileList);
	DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL,200000,3,"s3.amazonaws.com,project-open-data.cio.gov","testdownload");
	BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
	assertEquals(bundlePlan.getPostEach(),"_bundle");
	assertEquals(bundlePlan.getStatus(),"complete");
	assertEquals(bundlePlan.getBundleNameFilePathUrl().length, 1);
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length, 2);
	
    }
   
    @Test
    public void getBundleDownloadPlan2Test() throws JsonParseException, JsonMappingException, IOException{
	FilePathUrl[] inputfileList = new FilePathUrl[3];
	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1894/open-data.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";
	String val3 = "{\"filePath\":\"/1894/contract.pdf\",\"downloadUrl\":\"https://www.nist.gov/sites/default/files/documents/2018/12/26/letter_for_contractors_12.26.2018.pdf/\"}";

	ObjectMapper mapper = new ObjectMapper();
	FilePathUrl testval1 = mapper.readValue(val1, FilePathUrl.class);
	FilePathUrl testval2 = mapper.readValue(val2, FilePathUrl.class);
	FilePathUrl testval3 = mapper.readValue(val3, FilePathUrl.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	inputfileList[2] = testval3;
	BundleNameFilePathUrl bFL = new BundleNameFilePathUrl("testdownload", inputfileList);
	DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL,2000000,3,"s3.amazonaws.com,www.nist.gov","testdownload");
	BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
	assertEquals(bundlePlan.getPostEach(),"_bundle");
	assertEquals(bundlePlan.getStatus(),"warnings");
	assertEquals(bundlePlan.getBundleNameFilePathUrl().length, 1);
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getBundleName(), "testdownload-1.zip");
	assertEquals(bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length, 2);
	
    }
}
