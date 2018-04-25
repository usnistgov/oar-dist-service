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
package gov.nist.oar.distrib.storage;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.ds.exception.IDNotFoundException;
import gov.nist.oar.ds.exception.ResourceNotFoundException;
import gov.nist.oar.ds.s3.S3Wrapper;
import gov.nist.oar.ds.service.BagUtils;


/**
 * AWSS3LongTermStorage class connects  and gets data from AWS S3 storage , 
 * a long term storage for the data.
 * @author Deoyani Nandrekar-Heinis
 */
public class AWSS3LongTermStorage implements LongTermStorage {

  private static Logger logger = LoggerFactory.getLogger(AWSS3LongTermStorage.class);

  @Autowired
  AmazonS3 s3Client;
  
  @Value("${cloud.aws.preservation.s3.bucket}")
  String preservationBucket;

  @Value("${cloud.aws.cache.s3.bucket}")
  private String cacheBucket;
  
  /**
   * Given an exact file name in the storage, return an InputStream open at the start of the file
   * @param filename, Here filename refers to any file/object present in S3 bucket not the file inside compressed package
   * @return InputStream open at the start of the file
   * @throws FileNotFoundException
   */
  @Override
  public InputStream openFile(String filename) throws FileNotFoundException {
    logger.info(" Read the file from s3 bucket ::"+preservationBucket);
    GetObjectRequest getObjectRequest = new GetObjectRequest(preservationBucket, filename);
    S3Object s3Object = s3Client.getObject(getObjectRequest);
    return s3Object.getObjectContent();
  }

  /**
   * return the checksum for the given file
   * @param filename this is name of bag, checksum or any other object present in long term data storage\
   *        This parameter does not reflect the individual data file which might be present in the package/bundle/bag.
   * @return Checksum
   * @throws FileNotFoundException
   */
  @Override
  public Checksum getChecksum(String filename) throws FileNotFoundException {
    logger.info(" get the checksum from s3 bucket file."+preservationBucket);
    GetObjectRequest getObjectRequest = new GetObjectRequest(preservationBucket, filename+".sha256");

    S3Object s3Object = s3Client.getObject(getObjectRequest);
    BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()));
   
    String line;
    try{
        while((line = reader.readLine()) != null) {
          return Checksum.sha256(line);
       }
     }catch(IOException ie){
      throw new FileNotFoundException(ie.getMessage());
     }
     return null;
  }

  /**
   * Return the size of the named file in bytes
   * @param filename Here filename refers to any file/object present in S3 bucket not the file inside compressed package
   * @return long
   * @throws FileNotFoundException
   */
  @Override
  public long getSize(String filename) throws FileNotFoundException {
    logger.info(" getSize from s3 bucket"+ this.preservationBucket+"and  file : "+filename);
    return s3Client.getObjectMetadata(preservationBucket, filename).getContentLength();
  }

  /**
   * Return all the bags associated with the given ID
   * @param identifier is the unique record id for given data package
   * @return String 
   * @throws IDNotFoundException
   */
  @Override
  public List<String> findBagsFor(String identifier) throws IDNotFoundException {

    logger.info(" Get the list of filenames from s3 bucket which matches the given identifier.");
    ObjectListing objectListing = s3Client.listObjects(new ListObjectsRequest().withBucketName(preservationBucket));
    List<S3ObjectSummary> files = objectListing.getObjectSummaries();
    
    if (files.isEmpty()) {
      logger.error("No data available for given id.");
      throw new ResourceNotFoundException("No data available for given id.");
    } 
    List<String> filenames = new ArrayList<String>(files.size());
      files.forEach(new Consumer<S3ObjectSummary>() {
              public void accept(S3ObjectSummary f) {
                  String name = f.getKey();
                  if (name.endsWith(".zip") && BagUtils.isLegalBagName(name))
                    filenames.add(name);
              }
          }
      );
   
    return filenames;
  }

  /**
   * Return the head bag associated with the given ID
   * @param identifier is the unique record id for given data package
   * @return String
   * @throws IDNotFoundException
   */
  @Override
  public String findHeadBagFor(String identifier) throws IDNotFoundException {
    
    logger.info(" Get the head/latest bag for given identifier.");
    return BagUtils.findLatestHeadBag(this.findBagsFor(identifier));
  }

  /**
   * Return the name of the head bag for the identifier for given version
   * @param identifier is the unique record id for given data package
   * @param version 
   * @return String
   * @throws IDNotFoundException
   */
  @Override
  public String findHeadBagFor(String identifier, String version) throws IDNotFoundException {
    logger.info(" Get the bag for given identifier and version. ");
   
    return null;
  }

}
