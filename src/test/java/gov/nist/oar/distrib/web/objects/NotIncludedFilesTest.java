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
package gov.nist.oar.distrib.web.objects;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class NotIncludedFilesTest {
    
    @Test
    public void testNotIncludedFiles(){
	
	NotIncludedFiles notIn = new NotIncludedFiles("/testPath/testFile",
		"https://s3.amazonaws.com/nist-midas-org/1894/license.pdf", "Not allowed domain.");
	assertEquals("/testPath/testFile",notIn.getFilePath());
	assertEquals("https://s3.amazonaws.com/nist-midas-org/1894/license.pdf", notIn.getDownloadUrl());
	assertEquals("Not allowed domain.",notIn.getMessage());
    }
    
    @Test
    public void testNotIncludedFilesJson() throws JsonProcessingException, JSONException{
	String testJson = "{\"filePath\":\"/testPath/license.pdf\","
		+ "\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas-org/1894/license.pdf\","
		+ "\"message\": \"Not allowed domain.\"}";
	NotIncludedFiles notIn = new NotIncludedFiles("/testPath/license.pdf",
		"https://s3.amazonaws.com/nist-midas-org/1894/license.pdf", "Not allowed domain.");
	String json = new ObjectMapper().writeValueAsString(notIn);
	JSONAssert.assertEquals(testJson, json, true);
    }

}
