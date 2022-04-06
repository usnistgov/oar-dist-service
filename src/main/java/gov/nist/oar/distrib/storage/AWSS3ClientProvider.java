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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

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

    protected AmazonS3 s3 = null;
    protected String reg = null;
    protected AWSCredentialsProvider credpro = null;
    protected int acclim = 25;
    protected int accesses = 0;
    private String ep = null;
    static Logger log = LoggerFactory.getLogger(AWSS3ClientProvider.class);

    /**
     * create the provider
     * @param creds        the credentials to use when recreating the client
     * @param region       the AWS region to connect to
     * @param accessLimit  the maximum number of accesses allowed before the client is recreated;
     *                     a non-positive number will result in a new client with every access.
     */
    public AWSS3ClientProvider(AWSCredentialsProvider creds, String region, int accessLimit) {
        this(creds, region, accessLimit, null);
    }

    /**
     * create the provider.  This constructor is intended for use with an mock S3 service for 
     * testing purposes.
     * @param creds        the credentials to use when recreating the client
     * @param region       the AWS region to connect to
     * @param accessLimit  the maximum number of accesses allowed before the client is recreated;
     *                     a non-positive number will result in a new client with every access.
     * @param endpoint     the endpoint to use for the AWS s3 service
     */
    public AWSS3ClientProvider(AWSCredentialsProvider creds, String region,
                               int accessLimit, String endpoint)
    {
        credpro = creds;
        reg = region;
        acclim = accessLimit;
        ep = endpoint;
        refresh();
    }
    
    /**
     * return the maximum number of accesses allowed before the client is recreated
     */
    public int getAccessLimit() { return acclim; }

    /**
     * return the S3 client
     */
    public synchronized AmazonS3 client() {
        if (accesses >= acclim || s3 == null)
            refresh();
        accesses++;
        return s3;
    }

    /**
     * free up the client resources and recreate the client
     */
    public synchronized void refresh() {
        /*
        if (s3 != null)
            s3.shutdown();
        */
        log.info("FYI: Refreshing the S3 client");
        AmazonS3ClientBuilder bldr = AmazonS3Client.builder().standard()
                                                             .withCredentials(credpro);
        if (ep == null)
            bldr.withRegion(reg);
        else 
            bldr.withEndpointConfiguration(new EndpointConfiguration(ep, reg))
                .enablePathStyleAccess();
        s3 = bldr.build();

        accesses = 0;
    }

    /**
     * return the number of accesses are left before the client is refreshed
     */
    public int accessesLeft() {
        if (s3 == null)
            return 0;
        return acclim - accesses;
    }

    /**
     * free up resources by shutting down the client
     */
    public synchronized void shutdown() {
        if (s3 != null) {
            s3.shutdown();
            s3 = null;
        }
        accesses = 0;
    }

    public Object clone() {
        return new AWSS3ClientProvider(credpro, reg, acclim, ep);
    }
    public AWSS3ClientProvider cloneMe() {
        return (AWSS3ClientProvider) clone();
    }
}
