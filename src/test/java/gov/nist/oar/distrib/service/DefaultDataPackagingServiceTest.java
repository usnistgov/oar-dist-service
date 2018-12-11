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
package gov.nist.oar.distrib.service;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
public class DefaultDataPackagingServiceTest {
	
	private static Logger logger = LoggerFactory.getLogger(DefaultDataPackagingServiceTest.class);
	
	DefaultDataPackagingService ddp;
	static FilePathUrl[] requestedUrls = new FilePathUrl[2];
	long maxFileSize = 1000000;
	int numOfFiles = 100;
	String domains ="nist.gov, s3.amazonaws.com/nist-midas";
	static BundleNameFilePathUrl bundleRequest;
	
	public static void createRequest() throws JsonParseException, JsonMappingException, IOException{

		String val1 ="{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
		String val2 ="{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
		
		ObjectMapper mapper = new ObjectMapper();
		FilePathUrl testval1 = mapper.readValue(val1, FilePathUrl.class);
		FilePathUrl testval2 = mapper.readValue(val2, FilePathUrl.class);
		requestedUrls[0] = testval1;
		requestedUrls[1] = testval2;
		bundleRequest = new BundleNameFilePathUrl("testdatabundle",requestedUrls);
	}
	
	@BeforeClass
	public static void setUpClass() throws IOException {
	    createRequest();
	}
	
	@Before
	 public void setUp(){
		ddp = new DefaultDataPackagingService(domains,maxFileSize,numOfFiles,bundleRequest );
	}

	@Rule
    public final ExpectedException exception = ExpectedException.none();
	
	
	 @Test
	 public void getBundledZip() throws DistributionException, InputLimitException{
		 try {
			 String bundleName ="example";
			 ddp.validateRequest();
			 if(!bundleRequest.getBundleName().isEmpty() && bundleRequest.getBundleName() != null )
				 bundleName = bundleRequest.getBundleName();
			 
			Path path = Files.createTempFile(bundleName, ".zip");
			OutputStream os = Files.newOutputStream(path);
            ZipOutputStream zos = new ZipOutputStream(os);
            ddp.getBundledZipPackage(zos);
            zos.close();
            int len = (int) Files.size(path);
            System.out.println("Len:"+len);
            assertEquals(len,59903);
            checkFilesinZip(path);
            Files.delete(path);          
            		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 
	 }
	 public void checkFilesinZip(Path filepath){
		 
	        try(ZipFile file = new ZipFile(filepath.toString()))
	        {
	            FileSystem fileSystem = FileSystems.getDefault();
	            //Get file entries
	            Enumeration<? extends ZipEntry> entries = file.entries();
	            ZipEntry entry = entries.nextElement();
	            //Just check first entry in zip
	            assertEquals(entry.getName(),"/1894/license.pdf");
	          //Iterate over entries
//	            while (entries.hasMoreElements())
//	            {
//	            	ZipEntry entry = entries.nextElement();
//	            	if (entry.isDirectory()){
//	            		System.out.println("entryname:"+entry.getName());
//	            		 assertEquals(entry.getName(),entry.getName());
//	            	}
//	            }
	        }catch(IOException ixp){
	        	
	        }
	 }
}
