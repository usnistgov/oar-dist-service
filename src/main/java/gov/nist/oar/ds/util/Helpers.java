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
package gov.nist.oar.ds.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;



/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class Helpers {

	  String contentDispositionFormat = "attachment";
      String dsId = "";

	  Logger logger = LoggerFactory.getLogger(Helpers.class);
	
	  /**
	   * 
	   * @return
	   */
		public String getBagId(){
			return this.dsId ; 
		}
	
		public void setBagId(String dsId){
			this.dsId = dsId;
		}
		
//	/** Check file paths in groupDirectory.txt 
//	 *  For now simple solution added.
//	 * **/
//	public String getFilePath(String filename){
//		return this.dsId+"/data/"+filename;
//	}
	 
		/**
		 * Find the headbag id in given bucket
		 * @param lBags
		 * @return
		 */
	public String findHeadBag(List<String> lBags){
		logger.info("Get the head bag id");
		try{
		lBags.stream().sorted(new AlphanumComparator()).collect(Collectors.joining(" "));
		return lBags.get(lBags.size()-1);
		}catch(NullPointerException nxe){
			logger.info("Null pointer exception as there are bags with given recordid."+nxe.getMessage());
			return null;
		}catch(Exception e){
			logger.info("Exception as there are no bags with given recordid."+e.getMessage());
			return null;
		}
	}


	/**
	 * delete a non empty directory in Java
	 * @param dir
	 * @return
	 */
	public static boolean deleteDirectory(File dir) 
	{ 
		if (dir.isDirectory())
		{ 
			File[] children = dir.listFiles(); 
			for (int i = 0; i < children.length; i++) 
			{ 
				boolean success = deleteDirectory(children[i]);
				if (!success) { 
					return false;
				} 
			} 
		} 
		return dir.delete(); 
	}

	/***
	 * This section added to downloadall data from the cache directly
	 * @param filename
	 * @param outdata
	 * @return
	 */
	
	  public ResponseEntity<byte[]> getResponsEntity(String filename, byte[] outdata){
			
		  HttpHeaders httpHeaders = new HttpHeaders();
		    httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		    httpHeaders.setContentLength(outdata.length); 
		    httpHeaders.setContentDispositionFormData(contentDispositionFormat, filename);
		    return new ResponseEntity<>(outdata, httpHeaders, HttpStatus.OK);
	  }
	  
	  /**
	   * Extracts a zip entry (file entry) and puts in cacheBucket
	   * @param zipIn
	   * @param filePath
	   * @throws IOException
	   */
	  public void extractFile(ZipInputStream zipIn, String filePath, String key) throws IOException {
	      BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
	      byte[] bytesIn = new byte[9000];
	      int read = 0;
	      while ((read = zipIn.read(bytesIn)) != -1) {
	          bos.write(bytesIn, 0, read);
	      }
	      bos.close();
	  }

	  /***
	   * Read group-directory.txt and form the file path
	   * @throws IOException 
	   ***/

	  public String getFilepath(String filepath, InputStream inputStream) throws IOException{
	  	String objectPath = "";
	  	  // First read the group-directory.txt from headbag
	  	
	  	  try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
	  	      String line = null;
	  	      while((line = br.readLine()) != null) {
	  	          if(line.contains(filepath)){
	  	        	  String[] dsIdcontents = this.dsId.split("\\.");
	  	        	  String recordId = dsIdcontents[0];
	  	        	  String[] bucketPath = line.split(recordId);
	  	              objectPath = recordId + bucketPath[1]+"/" + bucketPath[0];
	  	              objectPath = objectPath.trim();
	  	          }
	  	      }
	  	      return objectPath;
	  	  } catch (IOException e) {
	  	     logger.info("Error in reading groupdir to form a filepath ::"+e.getMessage());
	  	      throw e;
	  	  }
	  }

}
