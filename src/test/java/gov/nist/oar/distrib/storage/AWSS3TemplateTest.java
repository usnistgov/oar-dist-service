package gov.nist.oar.distrib.storage;

import java.net.URI;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Template for AWS S3 unit tests using a mock server.
 * This provides a foundation to create new S3 unit tests.
 *
 * This class shows how to:
 * - Set up an S3 mock server.
 * - Initializw an S3Client for tests.
 * - Create, list, and clean up S3 resources during tests.
 */
public class AWSS3TemplateTest {

    @ClassRule
    public static S3MockTestRule s3MockRule = new S3MockTestRule();

    private static S3Client s3Client;
    private static final String BUCKET_NAME = "test-bucket";

    /**
     * Set up the S3 client and create a test bucket.
     */
    @BeforeClass
    public static void setUpClass() {
        // Initialize S3 client
        s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:9090/")) // Use mock server endpoint
                .forcePathStyle(true) // Path-style access for mock compatibility
                .build();

        // Create test bucket
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    }

    /**
     * Clean up the bucket and close the S3 client.
     */
    @AfterClass
    public static void tearDownClass() {
        // Delete all objects in the bucket
        s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET_NAME).build())
                .contents()
                .forEach(obj -> s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(obj.key())
                        .build()));

        // Delete the bucket
        s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build());

        // Close the S3 client
        s3Client.close();
    }

    /**
     * Example test for uploading and verifying an object in S3.
     */
    @Test
    public void testUploadAndRetrieveObject() {
        String key = "example.txt";
        String content = "Hello, S3!";

        // Upload an object
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromBytes(content.getBytes()));

        // Retrieve the object
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build());

        // Assert content matches
        Assert.assertEquals("Uploaded content should match retrieved content", content, response.asUtf8String());
    }

    /**
     * Example test for listing objects in a bucket.
     */
    @Test
    public void testListObjects() {
        // Upload some objects
        String[] keys = {"file1.txt", "file2.txt", "file3.txt"};
        for (String key : keys) {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(key)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromBytes("dummy content".getBytes()));
        }

        // List objects
        List<S3Object> objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .build()).contents();

        // Assert the correct number of objects are listed
        Assert.assertEquals("All uploaded objects should be listed", keys.length, objects.size());
    }

    /**
     * Example test for deleting an object from S3.
     */
    @Test
    public void testDeleteObject() {
        String key = "delete-me.txt";

        // Upload an object
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromBytes("temporary content".getBytes()));

        // Delete the object
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build());

        // Verify the object is deleted
        List<S3Object> objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .build()).contents();
        Assert.assertTrue("Deleted object should not exist in the bucket",
                objects.stream().noneMatch(obj -> obj.key().equals(key)));
    }
}
