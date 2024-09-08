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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.RequireWebSite;
import gov.nist.oar.distrib.datapackage.BundleDownloadPlan;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.DownloadBundlePlanner;
import gov.nist.oar.distrib.datapackage.FileRequest;
import gov.nist.oar.distrib.web.InvalidInputException;

@ExtendWith(RequireWebSite.class)  // Use the custom extension
public class DownloadBundlePlannerTest {

    @Test
    public void getBundleDownloadPlanTest()
            throws JsonParseException, JsonMappingException, IOException, DistributionException, InvalidInputException {
        FileRequest[] inputfileList = new FileRequest[2];
        String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
        String val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";

        ObjectMapper mapper = new ObjectMapper();
        FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
        FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
        inputfileList[0] = testval1;
        inputfileList[1] = testval2;
        BundleRequest bFL = new BundleRequest("testdownload", inputfileList, 0, 2);
        DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 200000, 3, "s3.amazonaws.com|project-open-data.cio.gov", "testdownload", 1);
        BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
        assertEquals("_bundle", bundlePlan.getPostEachTo());
        assertEquals("complete", bundlePlan.getStatus());
        assertEquals(1, bundlePlan.getBundleNameFilePathUrl().length);
        assertEquals(2, bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length);
    }

    @Test
    public void getBundleDownloadPlan2Test()
            throws JsonParseException, JsonMappingException, IOException, DistributionException, InvalidInputException {
        FileRequest[] inputfileList = new FileRequest[3];
        String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
        String val2 = "{\"filePath\":\"/1894/open-data.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";
        String val3 = "{\"filePath\":\"/1894/contract.pdf\",\"downloadUrl\":\"https://webbook.nist.gov/chemistry/faq/\"}";

        ObjectMapper mapper = new ObjectMapper();
        FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
        FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
        FileRequest testval3 = mapper.readValue(val3, FileRequest.class);
        inputfileList[0] = testval1;
        inputfileList[1] = testval2;
        inputfileList[2] = testval3;
        BundleRequest bFL = new BundleRequest("testdownload", inputfileList, 0, 3);
        DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 2000000, 3, "s3.amazonaws.com|nist.gov", "testdownload", 1);
        BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
        assertEquals("_bundle", bundlePlan.getPostEachTo());
        assertEquals("warnings", bundlePlan.getStatus());
        assertEquals(1, bundlePlan.getBundleNameFilePathUrl().length);
        assertEquals("testdownload-1.zip", bundlePlan.getBundleNameFilePathUrl()[0].getBundleName());
        assertEquals(2, bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length);
    }

    @Test
    public void getBundleDownloadPlan3Test()
            throws JsonParseException, JsonMappingException, IOException, DistributionException, InvalidInputException {
        FileRequest[] inputfileList = new FileRequest[3];
        String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
        String val2 = "{\"filePath\":\"/1894/open-data.pdf\",\"downloadUrl\":\"https://project-open-data.cio.gov/v1.1/schema/\"}";
        String val3 = "{\"filePath\":\"/1894/contract.pdf\",\"downloadUrl\":\"https://webbook.nist.gov/chemistry/faq/\"}";

        ObjectMapper mapper = new ObjectMapper();
        FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
        FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
        FileRequest testval3 = mapper.readValue(val3, FileRequest.class);
        inputfileList[0] = testval1;
        inputfileList[1] = testval2;
        inputfileList[2] = testval3;
        BundleRequest bFL = new BundleRequest("testdownload", inputfileList, 0, 3);
        DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 200, 2, "s3.amazonaws.com|nist.gov", "testdownload", 1);
        BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
        System.out.println("Bundle Plan: " + bundlePlan.getBundleNameFilePathUrl()[0].getBundleName());
        assertEquals("_bundle", bundlePlan.getPostEachTo());
        assertEquals("warnings", bundlePlan.getStatus());
        assertEquals(2, bundlePlan.getBundleNameFilePathUrl().length);
        assertEquals("testdownload-1.zip", bundlePlan.getBundleNameFilePathUrl()[0].getBundleName());
        assertEquals(1, bundlePlan.getBundleNameFilePathUrl()[0].getIncludeFiles().length);
    }

    @Test
    public void getBundleDownloadPlan4Test()
            throws JsonParseException, JsonMappingException, IOException, DistributionException, InvalidInputException {
        FileRequest[] ifileList = new FileRequest[2];
        String file1 = "{\"filePath\":\"someid/srd13_Al-001.json\",\"downloadUrl\":\"http://www.nist.gov/srd/srd_data/srd13_Al-00123.json\"}";
        String file2 = "{\"filePath\":\"someid/srd13_Al-002.json\",\"downloadUrl\":\"<html>This is test</html>http://www.nist.gov/srd/srd_data/srd13_Al-00123.json\"}";

        ObjectMapper mapper = new ObjectMapper();
        FileRequest fileRequest1 = mapper.readValue(file1, FileRequest.class);
        FileRequest fileRequest2 = mapper.readValue(file2, FileRequest.class);

        ifileList[0] = fileRequest1;
        ifileList[1] = fileRequest2;
        BundleRequest bFL = new BundleRequest("testdownload", ifileList, 0, 2);
        DownloadBundlePlanner dpl = new DownloadBundlePlanner(bFL, 200, 2, "s3.amazonaws.com|nist.gov|httpstat.us", "testdownload", 7);
        BundleDownloadPlan bundlePlan = dpl.getBundleDownloadPlan();
        assertEquals("_bundle", bundlePlan.getPostEachTo());
        assertEquals("Error", bundlePlan.getStatus());
    }
}
