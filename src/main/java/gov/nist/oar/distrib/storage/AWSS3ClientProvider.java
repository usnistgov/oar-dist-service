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

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * A wrapper for an AmazonS3Client that regulates is resource usage.  
 *
 * An AmazonS3Client maintains resources, including memory, file descriptors, and thread pools that 
 * can get used over time in a long-lived application.  This wrapper class limits the number of uses 
 * of an AmazonS3Client can be used before it is shutdown (cleaning up its resources) and recreated.
 * Users of this class access the client via the {@link #client()} method.  
 *
 * @deprecated not currently used
 */
public class AWSS3ClientProvider implements Cloneable {

    protected S3Client s3 = null;
    protected String region = null;
    protected AwsCredentialsProvider credProvider = null;
    protected int accessLimit = 25;
    protected int accesses = 0;
    private String endpoint = null;

    static Logger log = LoggerFactory.getLogger(AWSS3ClientProvider.class);

    /**
     * Create the provider.
     * @param creds       The credentials to use for S3.
     * @param region      The AWS region to connect to.
     * @param accessLimit Maximum number of accesses before recreating the client.
     */
    public AWSS3ClientProvider(AwsCredentialsProvider creds, String region, int accessLimit) {
        this(creds, region, accessLimit, null);
    }

    /**
     * Create the provider with a custom endpoint (e.g., for testing with a mock S3 service).
     * @param creds       The credentials to use for S3.
     * @param region      The AWS region to connect to.
     * @param accessLimit Maximum number of accesses before recreating the client.
     * @param endpoint    The custom endpoint URL.
     */
    public AWSS3ClientProvider(AwsCredentialsProvider creds, String region, int accessLimit, String endpoint) {
        this.credProvider = creds;
        this.region = region;
        this.accessLimit = accessLimit;
        this.endpoint = endpoint;
        refresh();
    }

    /**
     * Return the S3 client.
     */
    public synchronized S3Client client() {
        if (accesses >= accessLimit || s3 == null) {
            refresh();
        }
        accesses++;
        return s3;
    }

    /**
     * Refresh the S3 client (recreate it).
     */
    public synchronized void refresh() {
        log.info("Refreshing the S3 client");
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credProvider)
                .region(Region.of(region));

        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint))
                   .serviceConfiguration(S3Configuration.builder()
                           .pathStyleAccessEnabled(true)
                           .build());
        }

        if (s3 != null) {
            s3.close();
        }

        s3 = builder.build();
        accesses = 0;
    }

    /**
     * Shut down the S3 client and free resources.
     */
    public synchronized void shutdown() {
        if (s3 != null) {
            s3.close();
            s3 = null;
        }
        accesses = 0;
    }

    /**
     * Return the number of accesses left before a refresh is triggered.
     */
    public int accessesLeft() {
        if (s3 == null) return 0;
        return accessLimit - accesses;
    }

    @Override
    public Object clone() {
        return new AWSS3ClientProvider(credProvider, region, accessLimit, endpoint);
    }

    public AWSS3ClientProvider cloneMe() {
        return (AWSS3ClientProvider) clone();
    }
}