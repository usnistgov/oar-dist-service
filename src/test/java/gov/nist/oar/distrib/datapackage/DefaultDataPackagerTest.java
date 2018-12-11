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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.InputLimitException;
import gov.nist.oar.distrib.web.BundleNameFilePathUrl;
import gov.nist.oar.distrib.web.FilePathUrl;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DefaultDataPackagerTest {

	private static long mxFileSize = 1000000;
	private static int numberofFiles = 100;
	private static FilePathUrl[] inputfileList;
	private static BundleNameFilePathUrl bundleRequest;
	protected static Logger logger = LoggerFactory.getLogger(DefaultDataPackagerTest.class);
	DefaultDataPackager dp ;
	

	public static void createInput() throws JsonParseException, JsonMappingException, IOException{
	  
         inputfileList = new FilePathUrl[2];
		String val1 ="{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
		String val2 ="{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
		
		ObjectMapper mapper = new ObjectMapper();
		FilePathUrl testval1 = mapper.readValue(val1, FilePathUrl.class);
		FilePathUrl testval2 = mapper.readValue(val2, FilePathUrl.class);
		inputfileList[0] = testval1;
		inputfileList[1] = testval2;
	   
	}
	
	@BeforeClass
	public static void setUpClass() throws IOException {
	    createInput();
	}
	@Before
	public  void construct(){
		dp = new DefaultDataPackager(inputfileList,mxFileSize,numberofFiles);	
	}
	
	@Test
	public void testSize() throws MalformedURLException, IOException{
		assertEquals(dp.getSize(),62562);
	}
	
	@Test
	public void testNumOfFiles() throws IOException{
		assertEquals(dp.getFilesCount(),2);
	}
	
	@Test
	public void testValidateRequest() throws DistributionException, MalformedURLException, IOException, InputLimitException{
		this.createInput();
		dp = new DefaultDataPackager(inputfileList,mxFileSize,numberofFiles);	
		assertTrue(dp.getFilesCount()< this.numberofFiles);
		assertTrue(dp.getSize() < this.mxFileSize);
		int countBefore = 2;
		dp.validateRequest();
		int countAfter = dp.getFilesCount();
		assertTrue("No duplicates!", countBefore == countAfter);
	}
	
	@Test
	public void testValidateBundleRequest() throws DistributionException, MalformedURLException, IOException, InputLimitException{
		createBundleTestdata();
		int countBefore = 2;
		dp = new DefaultDataPackager(bundleRequest,mxFileSize,numberofFiles);	
		dp.validateInput();
		dp.validateBundleRequest();
		assertTrue(dp.getFilesCount()< this.numberofFiles);
		assertTrue(dp.getSize() < this.mxFileSize);
		int countAfter = dp.getFilesCount();	
		assertTrue("No duplicates!", countBefore == countAfter);
	}
	
	public void createBundleTestdata() throws JsonParseException, JsonMappingException, IOException{
		
		 inputfileList = new FilePathUrl[2];
			String val1 ="{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
			String val2 ="{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
			
			ObjectMapper mapper = new ObjectMapper();
			FilePathUrl testval1 = mapper.readValue(val1, FilePathUrl.class);
			FilePathUrl testval2 = mapper.readValue(val2, FilePathUrl.class);
			inputfileList[0] = testval1;
			inputfileList[1] = testval2;
			bundleRequest = new BundleNameFilePathUrl("testdatabundle",inputfileList);
	}
	

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    
	@Test
	public void testValidateBundleRequestError() throws DistributionException, MalformedURLException, IOException{
	
		exception.expect(JsonMappingException.class);
		createBundleErrorTestdata();

	}
	
	public void createBundleErrorTestdata() throws JsonParseException, JsonMappingException, IOException{
		
		 inputfileList = new FilePathUrl[2];
			String val1 ="{\"filePath\":\"/1894/license.pdf\",\"jgkdfghjkdf\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
			String val2 ="{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
			
			ObjectMapper mapper = new ObjectMapper();
			FilePathUrl testval1 = mapper.readValue(val1, FilePathUrl.class);
			FilePathUrl testval2 = mapper.readValue(val2, FilePathUrl.class);
			inputfileList[0] = testval1;
			inputfileList[1] = testval2;
			bundleRequest = new BundleNameFilePathUrl("testdatabundle",inputfileList);
	}
}
