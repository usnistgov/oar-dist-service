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

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

/**
 * This is the config class responsible of starting the s3 client in dev and prod environment
 *
 */
@Configuration
@Profile(value = {"dev", "prod"})
public class DevOrProdS3Config {

  private static Logger log = LoggerFactory.getLogger(DevOrProdS3Config.class);

  @Bean
  public AmazonS3Client s3Client() {
    log.info("Creating s3 client instance");
    InstanceProfileCredentialsProvider provider = new InstanceProfileCredentialsProvider();
    return (AmazonS3Client) AmazonS3ClientBuilder.standard().withCredentials(provider).build();
  }
}
