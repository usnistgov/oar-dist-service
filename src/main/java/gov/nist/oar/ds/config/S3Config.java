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
package gov.nist.oar.ds.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.client.builder.AwsClientBuilder;
/**
 * This is the config class responsible of starting the s3 client in dev and prod environment
 *
 */
@Configuration

public class S3Config {

  private static Logger log = LoggerFactory.getLogger(S3Config.class);

  @Bean
  @Profile({"prod",  "dev"})
  public AmazonS3Client s3Client() {
    log.info("Creating s3 client instance");
    InstanceProfileCredentialsProvider provider = new InstanceProfileCredentialsProvider();
    return (AmazonS3Client) AmazonS3ClientBuilder.standard().withCredentials(provider).build();
  }
  
  @Bean
//  @Profile({"default","test","aws"})
  @Profile({"default","test"})
  public AmazonS3 s3Clientlocal() {
	    log.info("Creating s3 client instance test aws:");
	   
	    return AmazonS3ClientBuilder
	              .standard()
	              .withPathStyleAccessEnabled(true)
	              .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:8001", "us-east-1"))
	              .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
	              .build();
	    
	    } 
  
}
