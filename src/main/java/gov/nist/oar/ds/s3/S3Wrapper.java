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
package gov.nist.oar.ds.s3;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
//import java.util.Base64;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
//import com.amazonaws.util.Base64;
import com.amazonaws.services.s3.model.CopyObjectRequest;

/**
 * This is the wrapper around the s3 client handling the connection to an s3 bucket
 * 
 *
 */
@Service
public class S3Wrapper {

  private static Logger log = LoggerFactory.getLogger(S3Wrapper.class);


  @Autowired
  private AmazonS3 s3Client;


  /**
   * Upload a file in an s3 bucket given the file stream content
   * 
   * @param bucket
   * @param inputStream
   * @param uploadKey
   * @return
 * @throws IOException 
   */
  public PutObjectResult upload(String bucket, InputStream inputStream, String uploadKey) throws IOException {
	  try{
		  
		  ObjectMetadata metadata = new ObjectMetadata();
		  //metadata.setContentLength(IOUtils.toByteArray(inputStream).length);
		  PutObjectRequest putObjectRequest =
				  new PutObjectRequest(bucket, uploadKey, inputStream, metadata);

		  putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);

		  return s3Client.putObject(putObjectRequest);
	  }catch(AmazonServiceException ae){
		 throw ae;  
	  } 
	  catch(AmazonClientException ace){
		  throw ace;
	  }
	  finally {
		   
		    if (inputStream != null) {
		        inputStream.close();
		    }
	  }
  }

  /**
   * Upload a collection of files to an s3 bucket
   * 
   * @param bucket
   * @param multipartFiles
   * @return
   */
  public List<PutObjectResult> upload(String bucket, MultipartFile[] multipartFiles) {
    List<PutObjectResult> putObjectResults = new ArrayList<>();
    Arrays.stream(multipartFiles)
        .filter(multipartFile -> !StringUtils.isEmpty(multipartFile.getOriginalFilename()))
        .forEach(multipartFile -> {
          try {
            putObjectResults.add(upload(bucket, multipartFile.getInputStream(),
                multipartFile.getOriginalFilename()));
          } catch (IOException e) {
            log.error(e.getLocalizedMessage());
            throw new IllegalArgumentException(e);
          }
        });

    return putObjectResults;
  }

  /**
   * Download a file from its key in an s3 bucket
   * 
   * @param bucket
   * @param key
   * @return
   * @throws IOException
   */
  public ResponseEntity<byte[]> download(String bucket, String key) throws IOException {
	 
    GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);

    S3Object s3Object = s3Client.getObject(getObjectRequest);

    S3ObjectInputStream objectInputStream = s3Object.getObjectContent();
   
    byte[] bytes = IOUtils.toByteArray(objectInputStream);

    String fileName = URLEncoder.encode(key, "UTF-8").replaceAll("\\+", "%20");

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    httpHeaders.setContentLength(bytes.length);
    httpHeaders.setContentDispositionFormData("attachment", fileName);

    return new ResponseEntity<>(bytes, httpHeaders, HttpStatus.OK);
  }
  
  
  public InputStream getS3Object(String bucket, String key){
	    
	    GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
	    
	    S3Object s3Object = s3Client.getObject(getObjectRequest);

	    return s3Object.getObjectContent();
  }
  
  public void copytocache(String hostbucket, String hostkey, String destBucket, String destKey){
	  CopyObjectRequest copyObjRequest = new CopyObjectRequest(hostbucket, hostkey, destBucket, destKey);
	  s3Client.copyObject(copyObjRequest);
  }
  
  public boolean doesObjectExistInCache(String cachebucket, String key){
	  return s3Client.doesObjectExist(cachebucket,key);
  }

  
  /**
   * List all buckets
   */
  public List<String> listBuckets(){
	  
	 List<Bucket> lBucket = s3Client.listBuckets();
	 List<String> listBucket = new ArrayList<String>();
	 for(int i =0; i<lBucket.size(); i++)
		 listBucket.add(lBucket.get(i).getName());
	 
	 return listBucket;
  }
  
  /**
   * List the files contained in a bucket
   * 
   * @param bucket
   * @return
   */
  public List<S3ObjectSummary> list(String bucket) {
	  
    ObjectListing objectListing =
        s3Client.listObjects(new ListObjectsRequest().withBucketName(bucket));
    return objectListing.getObjectSummaries();
  }

  /**
   * List a file contained in bucket starting witha a prefix
   * 
   * @param bucket
   * @param prefix
   * @param suffix
   * @return
   */
  public List<S3ObjectSummary> list(String bucket, String prefix) {
   
	  ObjectListing objectListing =
			  s3Client.listObjects(new ListObjectsRequest().withBucketName(bucket).withPrefix(prefix));

      return objectListing.getObjectSummaries();
  }

  
      
  public void putObject (String bucket, String key, ByteArrayInputStream input, int l) throws IOException{
	  
      ObjectMetadata metadata = new ObjectMetadata();	
//      byte[] resultByte = DigestUtils.md5(input);
//      String streamMD5 = new String(Base64.encodeBase64(resultByte));
//      metadata.setContentMD5(streamMD5);
      metadata.setContentLength(l);
    
      s3Client.putObject(bucket, key, input, metadata);
  }
  
  public void putObject (String bucket, String key, File f) throws IOException{
	  
      s3Client.putObject(bucket, key, f);
  }
  
  public void putObject (String bucket, String key, String content) throws IOException{
	  
    s3Client.putObject(bucket, key,content);
  }
}
