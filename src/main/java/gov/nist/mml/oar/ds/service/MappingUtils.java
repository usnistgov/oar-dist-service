package gov.nist.mml.oar.ds.service;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.springframework.http.ResponseEntity;

public class MappingUtils {
	
	/**
	 * Return the distribution file key from the ore mapping file
	 * @param dsId: the id of the distribution 
	 * @param oreContent: content of the mapping file 
	 * @return the key of the distribution file 
	 * @throws IOException
	 */
	public static String  findDistFileKey(String distId, String oreContent) throws IOException {
		return "001-"+ distId + ".png";
	}
	
	
}
