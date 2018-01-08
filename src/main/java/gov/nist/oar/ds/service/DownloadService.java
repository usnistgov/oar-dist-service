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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.model.PutObjectResult;

import gov.nist.oar.ds.exception.DistributionException;

/**
 * This is the download service class responsible of handling download request
 *
 */
public interface DownloadService {

//  /**
//   * Upload distribution files to the cache s3
//   * 
//   * @param multipartFiles
//   * @return
//   */
//  public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles);
//
//
//  /**
//   * Return a summary list of bags of a data set
//   * 
//   * @param dsId: id of the data set
//   * @return the list of keys of the bags
//   * @throws IOException
//   */
//  ResponseEntity<List<String>> findDataSetBags(String dsId) throws IOException;
//
//  /**
//   * 
//   * @param dsId
//   * @param distId
//   * @return
//   * @throws IOException
//   */
//  ResponseEntity<byte[]> downloadDistributionFile(String dsId, String distId) throws IOException;
//
//  /**
//   * Find the head bag of a data set by its id
//   * 
//   * @param dsId: id of the data set
//   * @return the head bag key
//   * @throws IOException
//   */
//  ResponseEntity<String> findDataSetHeadBag(String id) throws IOException;

  /**
   * 
   * @param Id
   * @return zip byte[] 
   * @throws IOException
   */
  ResponseEntity<byte[]> downloadZipFile(String id) throws DistributionException; 
	
//	ResponseEntity<byte[]> downloadAllData(String recordid) throws Exception;
	
	ResponseEntity<byte[]> downloadData(String recordid, String filepath) throws IOException;
  
}
