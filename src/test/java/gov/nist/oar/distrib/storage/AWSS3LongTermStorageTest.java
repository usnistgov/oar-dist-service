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

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import gov.nist.oar.ds.config.S3Config;
import gov.nist.oar.ds.exception.IDNotFoundException;

//@SpringBootTest
////@SpringBootConfiguration
//@ContextConfiguration(classes = {S3Config.class})
@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource(properties = {
                                  "test.bucket=nist-oar-dev-pres",})


/**
 * This is test class is used to connect to long term storage on AWS S3
 * To test AWSS3LongTermStorage
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class AWSS3LongTermStorageTest {
  
  private static Logger logger = LoggerFactory.getLogger(FilesystemLongTermStorageTest.class);

  @Value("${test.bucket}")
  String dataDir;
  
  private AWSS3LongTermStorage s3Storage;// = new AWSS3LongTermStorage();
  
  @Before
  public void beforeSetup()
  {
    s3Storage = new AWSS3LongTermStorage();
    s3Storage.preservationBucket = dataDir;
    s3Storage.s3Client = this.beforeEverything();
  }
  
  public AmazonS3 beforeEverything(){
    return AmazonS3ClientBuilder
    .standard()
    .withPathStyleAccessEnabled(true)
    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8001", "us-east-1"))
    .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
    .build();
  }

  
  @Test
  public void testFileList() throws IDNotFoundException {
    
    List<String> filenames = new ArrayList<>();
         filenames.add("6376FC675D0E1D77E0531A5706812BC21886.mbag0_2-0.zip");
    assertEquals(s3Storage.findBagsFor("6376FC675D0E1D77E0531A5706812BC21886"),filenames);
  }
  
  @Test
  public void testFileChecksum() throws FileNotFoundException  {
    String hash="379171a0ca303d741f854fb427fc6cfa54f9a0d900c4db64a407fc5b7d81706a";
    String getChecksumHash = s3Storage.getChecksum("6376FC675D0E1D77E0531A5706812BC21886.mbag0_2-0.zip").hash;
    assertEquals(getChecksumHash.trim(),hash.trim());
  }
  
  @Test
  public void testFileSize() throws FileNotFoundException  {
    long filelength = s3Storage.getSize("6376FC675D0E1D77E0531A5706812BC21886.mbag0_2-0.zip");
    assertEquals(filelength,31145);
  } 
      
  @Test
  public void testFileStream() throws FileNotFoundException  {
    InputStream is = s3Storage.openFile("6376FC675D0E1D77E0531A5706812BC21886.mbag0_2-0.zip");
    assertEquals(is,is);
  } 

}
