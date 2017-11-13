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
 * 
 * @author:Harold Affo (Prometheus Computing, LLC)
 */
package gov.nist.oar.ds.service;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;

/**
 * This is the download service class responsible of handling download request
 *
 */
public interface DownloadService {

  /**
   * Return a summary list of bags of a data set
   * 
   * @param dsId: id of the data set
   * @return the list of keys of the bags
   * @throws IOException
   */
	ResponseEntity<List<String>> findDataSetBags(String bucket, String dsId) throws IOException;

   /* Find the head bag of a data set by its id
   * 
   * @param dsId: id of the data set
   * @return the head bag key
   * @throws IOException
   */

  	ResponseEntity<String> findDataSetHeadBag(String bucket, String id) throws IOException;

  /**
   * Download all the data in zip format
   * @param Id
   * @return zip byte[] 
   * @throws IOException
   */
  	ResponseEntity<byte[]> downloadZipFile(String id) throws Exception; 
	
  	/***
  	 * Download individual file for given data
  	 * @param recordid
  	 * @param filepath
  	 * @return
  	 * @throws Exception
  	 */
	ResponseEntity<byte[]> downloadData(String recordid, String filepath) throws Exception;
	
	/***
	 * Extract and load cache for given id
	 * @param id
	 * @throws Exception
	 */
	void extractAlltoCache(String id) throws Exception;
   
	/**
	 * Test data
	 * @return
	 * @throws Exception
	 */
	ResponseEntity<byte[]> getdownloadtest() throws Exception;
//	ResponseEntity<byte[]> downloadAllData(String recordid) throws Exception;
}
