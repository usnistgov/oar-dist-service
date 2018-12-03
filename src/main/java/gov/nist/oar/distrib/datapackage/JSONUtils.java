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

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.web.FilePathUrl;

public final class JSONUtils {
  public JSONUtils(){}

  public static boolean isJSONValid(String jsonInString ) {
    try {
    	
    	
       final ObjectMapper mapper = new ObjectMapper();
       mapper.readTree(jsonInString);
       return true;
    } catch (IOException e) {
       return false;
    }
  }
  
  public static boolean isJSONValid(FilePathUrl[] jsonInString ) {
	    try {
	    	
	    	
	       final ObjectMapper mapper = new ObjectMapper();
	       String test = mapper.writeValueAsString(jsonInString);
	       System.out.println(test);
	       return true;
	    } catch (IOException e) {
	       return false;
	    }
	  }
}
