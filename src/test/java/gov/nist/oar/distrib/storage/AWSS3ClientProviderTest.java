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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;

public class AWSS3ClientProviderTest {

    private static final String bucket = "oar-lts-test";
    private AWSS3ClientProvider s3Provider = null;

    @BeforeClass
    public static void setUpClass() {
        S3Client s3client = createS3Provider().client();

        if (bucketExists(s3client, bucket)) {
            destroyBucket(s3client);
        }

        // Create bucket
        s3client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }

    public static AWSS3ClientProvider createS3Provider() {
        final AwsBasicCredentials credentials = AwsBasicCredentials.create("foo", "bar");
        final String endpoint = "http://localhost:9090/";
        final String region = "us-east-1";

        return new AWSS3ClientProvider(StaticCredentialsProvider.create(credentials), region, 2, endpoint);
    }

    private static boolean bucketExists(S3Client s3client, String bucketName) {
        try {
            s3client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    private static void destroyBucket(S3Client s3client) {
        ListObjectsV2Response listResponse = s3client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .build());

        // Delete all objects
        List<String> keys = listResponse.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
        for (String key : keys) {
            s3client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        }

        // Delete the bucket
        s3client.deleteBucket(DeleteBucketRequest.builder()
                .bucket(bucket)
                .build());
    }

    @AfterClass
    public static void tearDownClass() {
        S3Client s3client = createS3Provider().client();
        destroyBucket(s3client);
    }

    @Before
    public void setUp() {
        s3Provider = createS3Provider();
    }

    @Test
    public void testClient() {
        assertNotNull(s3Provider);
        assertEquals(2, s3Provider.accessesLeft());

        S3Client client1 = s3Provider.client();
        assertNotNull(client1);
        assertEquals(1, s3Provider.accessesLeft());

        S3Client client2 = s3Provider.client();
        assertNotNull(client2);
        assertSame(client1, client2);
        assertEquals(0, s3Provider.accessesLeft());

        // Client should reset after limit is exceeded
        S3Client client3 = s3Provider.client();
        assertNotNull(client3);
        assertNotSame(client1, client3);
        assertEquals(1, s3Provider.accessesLeft());

        // Validate bucket existence
        assertTrue(bucketExists(client3, bucket));
    }

    @Test
    public void testShutdown() {
        S3Client client = s3Provider.client();
        assertNotNull(client);
        assertEquals(1, s3Provider.accessesLeft());

        s3Provider.shutdown();
        assertEquals(0, s3Provider.accessesLeft());

        try {
            client.listBuckets();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            // Expected
        }

        // Create a new client after shutdown
        S3Client newClient = s3Provider.client();
        assertNotNull(newClient);
        assertTrue(bucketExists(newClient, bucket));
    }

    @Test
    public void testClone() {
        assertNotNull(s3Provider);
        assertEquals(2, s3Provider.accessesLeft());

        S3Client client1 = s3Provider.client();
        assertNotNull(client1);
        assertEquals(1, s3Provider.accessesLeft());

        AWSS3ClientProvider clonedProvider = s3Provider.cloneMe();
        assertNotSame(s3Provider, clonedProvider);
        assertEquals(2, clonedProvider.accessesLeft());

        S3Client client2 = clonedProvider.client();
        assertNotNull(client2);
        assertNotSame(client1, client2);
        assertEquals(1, clonedProvider.accessesLeft());
    }
}