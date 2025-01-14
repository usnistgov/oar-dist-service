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
package gov.nist.oar.distrib.datapackage;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * @author Deoyani Nandrekar-Heinis BundleNamefilePathUrl java object test.
 *
 */
public class BundleNameFilePathUrlTest {

    @Test
    public void testBundleNameFilePathUrl() {
	FileRequest fpathUrl_1 = new FileRequest("/filepath/file-1.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	FileRequest fpathUrl_2 = new FileRequest("/filepath/file-2.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	BundleRequest bundle1 = new BundleRequest("download_data_1",
		new FileRequest[] { fpathUrl_1, fpathUrl_2 },0,2);

	assertEquals("download_data_1", bundle1.getBundleName());
	assertEquals("/filepath/file-1.pdf", bundle1.getIncludeFiles()[0].getFilePath());
	assertEquals("https://s3.amazonaws.com/nist-midas/1894/license.pdf",
		bundle1.getIncludeFiles()[0].getDownloadUrl());

    }

    @Test
    public void testJson() throws JsonProcessingException, JSONException {
	// String fileUrl1 = "{\"filePath\":\"/filepath/file-1.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	// String fileUrl2 = "{\"filePath\":\"/filepath/file-2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";

	// String bundleNAme = " \"requestId\": request-1, \"bundleName\":\"download_data_1\" ";
	// String bundleJson = "{" + bundleNAme + "," + "\"includeFiles\"" + ":[" + fileUrl1 + "," + fileUrl2 + "]" 
	// + ", \"bundleSize\": 66, \"filesInBundle\": 2 }";
	// JSONObject jsonObject = new JSONObject(bundleJson);
	FileRequest fpathUrl_1 = new FileRequest("/filepath/file-1.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	FileRequest fpathUrl_2 = new FileRequest("/filepath/file-2.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	BundleRequest bundle1 = new BundleRequest("download_data_1",
		new FileRequest[] { fpathUrl_1, fpathUrl_2 },66,2, "request-1");

	JSONAssert.assertEquals("request-1", bundle1.getRequestId(), true);
	//JSONAssert.assertEquals(bundleJson, json, true);
    }

    public BundleRequest[] makeBundles() {
	FileRequest fpathUrl_1 = new FileRequest("/filepath/file-1.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	FileRequest fpathUrl_2 = new FileRequest("/filepath/file-2.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	BundleRequest bundle1 = new BundleRequest("download_data_1",
		new FileRequest[] { fpathUrl_1, fpathUrl_2 },0,2);

	FileRequest fpathUrl_3 = new FileRequest("/filepath-2/testfile-1.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	FileRequest fpathUrl_4 = new FileRequest("/filepath-2/testfile-2.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	BundleRequest bundle2 = new BundleRequest("download_data_2",
		new FileRequest[] { fpathUrl_3, fpathUrl_4 },0,4);

	return new BundleRequest[] { bundle1, bundle2 };
    }

}
