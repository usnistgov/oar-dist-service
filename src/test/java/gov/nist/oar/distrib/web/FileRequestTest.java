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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.datapackage.FileRequest;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class FileRequestTest {

    @Test
    public void testFilePathUrl() {
        FileRequest fpathUrl = new FileRequest("/1894/license.pdf",
                "https://s3.amazonaws.com/nist-midas/1894/license.pdf");
        assertEquals("/1894/license.pdf", fpathUrl.getFilePath());
        assertEquals("https://s3.amazonaws.com/nist-midas/1894/license.pdf", fpathUrl.getDownloadUrl());
    }

    @Test
    public void testJson() throws JsonProcessingException, JSONException {
        String testJson = "{\"filePath\":\"/1894/license.pdf\","
                + "\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\","
                + "\"fileSize\":0}";
        FileRequest fpathUrl = new FileRequest("/1894/license.pdf",
                "https://s3.amazonaws.com/nist-midas/1894/license.pdf");
        String json = new ObjectMapper().writeValueAsString(fpathUrl);
        JSONAssert.assertEquals(testJson, json, true);
    }
}
