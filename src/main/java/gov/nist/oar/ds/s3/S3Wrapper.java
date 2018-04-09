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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

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
   */
  private PutObjectResult upload(String bucket, InputStream inputStream, String uploadKey) {
    PutObjectRequest putObjectRequest =
        new PutObjectRequest(bucket, uploadKey, inputStream, new ObjectMetadata());

    putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);

    PutObjectResult putObjectResult = s3Client.putObject(putObjectRequest);

    IOUtils.closeQuietly(inputStream);

    return putObjectResult;
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
  
  public void copytocache(String hostbucket, String hostkey, String destBucket, String destKey){
	  CopyObjectRequest copyObjRequest = new CopyObjectRequest(hostbucket, hostkey, destBucket, destKey);
	  s3Client.copyObject(copyObjRequest);
  }
  
  public boolean doesObjectExistInCache(String cachebucket, String key){
	  return s3Client.doesObjectExist(cachebucket,key);
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

}
