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

import java.io.IOException;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.oar.distrib.web.objects.BundleRequest;
import gov.nist.oar.distrib.web.objects.FileRequest;

/**
 * JSONUtils class provides some static functions to parse and validate json
 * data.
 * 
 * @author Deoyani Nandrekar-Heinis
 *
 */
public final class JSONUtils {

    private static String bundleName = "";
    protected static Logger logger = LoggerFactory.getLogger(JSONUtils.class);
    
    private JSONUtils() {
	// Default
    }

    /**
     * Read jsonstring to check validity
     * 
     * @param jsonInString
     * @return boolean
     */
    public static boolean isJSONValid(String jsonInString) {
	try {
	    final ObjectMapper mapper = new ObjectMapper();
	    mapper.readTree(jsonInString);
	    return true;
	} catch (IOException e) {
	    return false;
	}
    }

    /**
     * Check input filePathURL is valid json
     * 
     * @param jsonInString
     * @return boolean
     */
    public static boolean isJSONValid(FileRequest[] jsonInString) {
	try {
	    final ObjectMapper mapper = new ObjectMapper();
	    String test = mapper.writeValueAsString(jsonInString);
	    List<FileRequest> myObjects = Arrays.asList(jsonInString);
	    if (myObjects.contains(null)){
		logger.error("Error in isJSONValid, there are null values in input.");
		throw new IOException("There are null values in the input.");
	    }
	    return true;
	} catch (IOException e) {
	    return false;
	}
    }

    /**
     * Check whether input is valid json
     * 
     * @param inputJson
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    public static void isJSONValid(BundleRequest inputJson)
	    throws JsonParseException, JsonMappingException, IOException {
	try {
	    final ObjectMapper mapper = new ObjectMapper();
	    String test = mapper.writeValueAsString(inputJson);
	    if (inputJson.getBundleName() == null)
		bundleName = "download";
	    else
		bundleName = inputJson.getBundleName();
	    isJSONValid(inputJson.getIncludeFiles());
	} catch (IOException e) {
	    logger.error("The input json is not valid."+e.getMessage());
	    throw new IOException("The input json is not valid.");
	}
    }
}
