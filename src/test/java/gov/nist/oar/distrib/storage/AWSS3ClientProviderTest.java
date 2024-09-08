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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class AWSS3ClientProviderTest {

    static AmazonS3 s3client = null;
    static final String bucket = "oar-lts-test";

    @BeforeAll
    public static void setUpClass() throws IOException {
        s3client = createS3Client();

        if (s3client.doesBucketExistV2(bucket))
            destroyBucket();
        s3client.createBucket(bucket);
        // populateBucket(s3client);
    }

    public static AmazonS3 createS3Client() {
        final BasicAWSCredentials credentials = new BasicAWSCredentials("foo", "bar");
        final String endpoint = "http://localhost:9090/";
        final String region = "us-east-1";

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(new AmazonS3ClientBuilder.EndpointConfiguration(endpoint, region))
                .enablePathStyleAccess()
                .build();
    }

    @AfterAll
    public static void tearDownClass() {
        destroyBucket();
    }

    public static void destroyBucket() {
        List<S3ObjectSummary> files = s3client.listObjects(bucket).getObjectSummaries();
        for (S3ObjectSummary f : files) 
            s3client.deleteObject(bucket, f.getKey());
        s3client.deleteBucket(bucket);
    }

    @BeforeEach
    public void setUp() {
        s3client = createS3Client();
    }

    @AfterEach
    public void tearDown() {
        s3client.shutdown();
    }

    @Test
    public void testClient() {
        assertNotNull(s3client);
        assertTrue(s3client.doesBucketExistV2(bucket));
    }

    @Test
    public void testShutdown() {
        assertNotNull(s3client);

        s3client.shutdown();
        try {
            s3client.doesBucketExistV2(bucket);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ex) {
            // Expected
        }

        // Reinitialize after shutdown
        s3client = createS3Client();
        assertTrue(s3client.doesBucketExistV2(bucket));
    }

    @Test
    public void testClone() {
        AmazonS3 newS3client = createS3Client();
        assertNotNull(newS3client);
        assertNotEquals(s3client, newS3client);

        assertTrue(newS3client.doesBucketExistV2(bucket));
    }
}
