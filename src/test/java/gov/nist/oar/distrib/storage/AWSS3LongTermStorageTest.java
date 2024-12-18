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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * This is test class is used to connect to long term storage on AWS S3
 * To test AWSS3LongTermStorage
 *
 * @author Deoyani Nandrekar-Heinis
 */
public class AWSS3LongTermStorageTest {

    @ClassRule
    public static S3MockTestRule siterule = new S3MockTestRule();

    private static Logger logger = LoggerFactory.getLogger(AWSS3LongTermStorageTest.class);

    private static final String BUCKET_NAME = "oar-lts-test";
    private static final String HASH = "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9";
    private static S3Client s3client;
    AWSS3LongTermStorage s3Storage = null;

    @BeforeClass
    public static void setUpClass() {
        // Initialize S3 Client
        s3client = createS3Client();

        // Ensure bucket is clean before tests
        if (bucketExists(BUCKET_NAME)) {
            destroyBucket();
        }
        s3client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
        populateBucket();
    }

    @Before
    public void setUp() throws IOException {
        try {
            s3Storage = new AWSS3LongTermStorage(BUCKET_NAME, s3client);
        } catch (FileNotFoundException | StorageVolumeException ex) {
            throw new IllegalStateException(
                    "Failed to initialize AWSS3LongTermStorage for test setup: " + ex.getMessage(), ex);
        }
    }

    @After
    public void tearDown() {
        s3Storage = null;
    }

    @AfterClass
    public static void tearDownClass() {
        try {
            if (s3client != null) {
                destroyBucket();
            }
        } catch (Exception e) {
            logger.warn("Failed to destroy bucket during teardown: " + e.getMessage());
        }
        siterule.stopServer();
    }

    public static S3Client createS3Client() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("foo", "bar")))
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:9090/")) // Mock server endpoint
                .forcePathStyle(true)
                .build();
    }

    public static boolean bucketExists(String bucketName) {
        try {
            s3client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    public static void destroyBucket() {
        s3client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET_NAME).build())
                .contents()
                .forEach(object -> s3client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(object.key())
                        .build()));
        s3client.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build());
    }

    @Test
    public void testBucketCreation() {
        try {
            s3client.headBucket(HeadBucketRequest.builder().bucket(BUCKET_NAME).build());
            assertTrue("Bucket exists and is accessible", true);
        } catch (Exception e) {
            fail("Bucket creation or access failed: " + e.getMessage());
        }
    }

    public static void populateBucket() {
        String[] bases = {
                "mds013u4g.1_0_0.mbag0_4-", "mds013u4g.1_0_1.mbag0_4-", "mds013u4g.1_1.mbag0_4-",
                "mds088kd2.mbag0_3-", "mds088kd2.mbag0_3-", "mds088kd2.1_0_1.mbag0_4-"
        };

        int j = 0;
        for (String base : bases) {
            for (int i = 0; i < 3; i++) {
                String bag = base + j++ + ((i > 1) ? ".7z" : ".zip");
                String baghash = HASH + " " + bag;

                // Check if the object already exists
                if (!objectExists(BUCKET_NAME, bag)) {
                    // Upload the empty "bag" file
                    try (InputStream ds = new ByteArrayInputStream("0".getBytes())) {
                        s3client.putObject(PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(bag)
                                .contentType("text/plain")
                                .contentLength(1L)
                                .build(), RequestBody.fromInputStream(ds, 1L));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to upload file: " + bag, e);
                    }

                    // Upload the "baghash" file
                    try (InputStream ds = new ByteArrayInputStream(baghash.getBytes())) {
                        s3client.putObject(PutObjectRequest.builder()
                                .bucket(BUCKET_NAME)
                                .key(bag + ".sha256")
                                .contentType("text/plain")
                                .contentLength((long) baghash.length())
                                .build(), RequestBody.fromInputStream(ds, baghash.length()));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to upload hash file: " + bag + ".sha256", e);
                    }
                }
            }
        }
    }

    private static boolean objectExists(String bucket, String key) {
        try {
            s3client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception e) {
            if (e instanceof NoSuchKeyException || e.statusCode() == 404) {
                return false; // Object does not exist
            }
            throw new RuntimeException("Error checking object existence: " + key, e);
        }
    }

    @Test
    public void testBucketContents() {
        ListObjectsV2Response response = s3client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET_NAME).build());
        List<String> keys = response.contents().stream().map(S3Object::key).collect(Collectors.toList());
        assertTrue(keys.contains("mds013u4g.1_0_0.mbag0_4-0.zip"));
        assertTrue(keys.contains("mds013u4g.1_0_0.mbag0_4-2.7z"));
    }

    @Test
    public void testPopulateBucket() {
        try {
            // Call for populateBucket was done in class setup

            // Verify that all expected objects are present in the bucket
            String[] expectedKeys = {
                    "mds013u4g.1_0_0.mbag0_4-0.zip", "mds013u4g.1_0_0.mbag0_4-1.zip", "mds013u4g.1_0_0.mbag0_4-2.7z",
                    "mds013u4g.1_0_1.mbag0_4-3.zip", "mds013u4g.1_0_1.mbag0_4-4.zip", "mds013u4g.1_0_1.mbag0_4-5.7z",
                    "mds013u4g.1_1.mbag0_4-6.zip", "mds013u4g.1_1.mbag0_4-7.zip", "mds013u4g.1_1.mbag0_4-8.7z",
                    "mds088kd2.mbag0_3-9.zip", "mds088kd2.mbag0_3-10.zip", "mds088kd2.mbag0_3-11.7z",
                    "mds088kd2.mbag0_3-12.zip", "mds088kd2.mbag0_3-13.zip", "mds088kd2.mbag0_3-14.7z",
                    "mds088kd2.1_0_1.mbag0_4-15.zip", "mds088kd2.1_0_1.mbag0_4-16.zip", "mds088kd2.1_0_1.mbag0_4-17.7z"
            };

            // Verify each object exists in the bucket
            for (String key : expectedKeys) {
                assertTrue("Object " + key + " should exist in the bucket",
                        objectExists(BUCKET_NAME, key));
            }

            // Verify corresponding .sha256 files exist
            for (String key : expectedKeys) {
                String hashKey = key + ".sha256";
                assertTrue("Hash file " + hashKey + " should exist in the bucket",
                        objectExists(BUCKET_NAME, hashKey));
            }
        } catch (Exception e) {
            fail("populateBucket test failed: " + e.getMessage());
        }
    }

    @Test
    public void testFindBagsFor() throws DistributionException, FileNotFoundException {
        List<String> filenames = new ArrayList<String>();
        filenames.add("mds013u4g.1_0_0.mbag0_4-0.zip");
        filenames.add("mds013u4g.1_0_0.mbag0_4-1.zip");
        filenames.add("mds013u4g.1_0_0.mbag0_4-2.7z");
        filenames.add("mds013u4g.1_0_1.mbag0_4-3.zip");
        filenames.add("mds013u4g.1_0_1.mbag0_4-4.zip");
        filenames.add("mds013u4g.1_0_1.mbag0_4-5.7z");
        filenames.add("mds013u4g.1_1.mbag0_4-6.zip");
        filenames.add("mds013u4g.1_1.mbag0_4-7.zip");
        filenames.add("mds013u4g.1_1.mbag0_4-8.7z");

        assertEquals(filenames, s3Storage.findBagsFor("mds013u4g"));

        try {
            filenames = s3Storage.findBagsFor("mds013u4g9");
            fail("Failed to raise ResourceNotFoundException; returned " + filenames.toString());
        } catch (ResourceNotFoundException ex) {
        }

        filenames = new ArrayList<String>();
        filenames.add("mds088kd2.mbag0_3-9.zip");
        filenames.add("mds088kd2.mbag0_3-10.zip");
        filenames.add("mds088kd2.mbag0_3-11.7z");
        filenames.add("mds088kd2.mbag0_3-12.zip");
        filenames.add("mds088kd2.mbag0_3-13.zip");
        filenames.add("mds088kd2.mbag0_3-14.7z");
        filenames.add("mds088kd2.1_0_1.mbag0_4-15.zip");
        filenames.add("mds088kd2.1_0_1.mbag0_4-16.zip");
        filenames.add("mds088kd2.1_0_1.mbag0_4-17.7z");
        filenames.sort(null);

        List<String> found = s3Storage.findBagsFor("mds088kd2");
        found.sort(null);

        assertEquals(filenames, found);
    }

    @Test
    public void testFindBagsForByPage() throws DistributionException, FileNotFoundException {
        s3Storage.setPageSize(4);
        List<String> filenames = new ArrayList<String>();
        filenames.add("mds013u4g.1_0_0.mbag0_4-0.zip");
        filenames.add("mds013u4g.1_0_0.mbag0_4-1.zip");
        filenames.add("mds013u4g.1_0_0.mbag0_4-2.7z");
        filenames.add("mds013u4g.1_0_1.mbag0_4-3.zip");
        filenames.add("mds013u4g.1_0_1.mbag0_4-4.zip");
        filenames.add("mds013u4g.1_0_1.mbag0_4-5.7z");
        filenames.add("mds013u4g.1_1.mbag0_4-6.zip");
        filenames.add("mds013u4g.1_1.mbag0_4-7.zip");
        filenames.add("mds013u4g.1_1.mbag0_4-8.7z");

        assertEquals(filenames, s3Storage.findBagsFor("mds013u4g"));
    }

    @Test
    public void testFileChecksum() throws FileNotFoundException, DistributionException {
        String getChecksumHash = s3Storage.getChecksum("mds088kd2.mbag0_3-10.zip").hash;
        assertEquals(getChecksumHash.trim(), HASH.trim());

        String h = "6f6173bf926eef7978d86a98f19ebc54b14ce3f8acaa2ce7dc8d199ae65adcb7";
        getChecksumHash = s3Storage.getChecksum("mds088kd2.mbag0_3-10.zip.sha256").hash;
        assertEquals(h.trim(), getChecksumHash.trim());
    }

    @Test
    public void testFileSize() throws FileNotFoundException, DistributionException {
        long filelength = s3Storage.getSize("mds088kd2.1_0_1.mbag0_4-17.7z");
        assertEquals(1, filelength);

        filelength = s3Storage.getSize("mds088kd2.1_0_1.mbag0_4-17.7z.sha256");
        assertEquals(94, filelength);
    }

    // Need to update deatils to compare two file streams
    @Test
    public void testFileStream() throws FileNotFoundException, DistributionException, IOException {
        InputStream is = s3Storage.openFile("mds088kd2.1_0_1.mbag0_4-17.7z");
        byte[] buf = new byte[100];
        int n = is.read(buf);
        assertEquals("Unexpected output: " + (new String(buf, 0, n)), 1, n);
        assertEquals("0", new String(buf, 0, 1));
        assertEquals(-1, is.read());
        is.close();

        is = s3Storage.openFile("mds088kd2.1_0_1.mbag0_4-17.7z.sha256");
        assertEquals(94, is.read(buf));
        assertEquals(-1, is.read());
        is.close();

        try {
            is = s3Storage.openFile("goober-17.7z");
            fail("Failed to barf on missing file");
            is.close();
        } catch (FileNotFoundException ex) {
        }
    }

    @Test
    public void testFileHeadbag() throws FileNotFoundException, DistributionException {
        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", s3Storage.findHeadBagFor("mds088kd2"));
        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", s3Storage.findHeadBagFor("mds013u4g"));

        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", s3Storage.findHeadBagFor("mds013u4g", "1.1"));
        assertEquals("mds013u4g.1_0_1.mbag0_4-5.7z", s3Storage.findHeadBagFor("mds013u4g", "1.0.1"));
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z", s3Storage.findHeadBagFor("mds013u4g", "1.0.0"));

        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", s3Storage.findHeadBagFor("mds088kd2", "1.0.1"));
        assertEquals("mds088kd2.mbag0_3-14.7z", s3Storage.findHeadBagFor("mds088kd2", "0"));
        assertEquals("mds088kd2.mbag0_3-14.7z", s3Storage.findHeadBagFor("mds088kd2", "1"));

        try {
            String bagname = s3Storage.findHeadBagFor("mds013u4g9");
            fail("Failed to raise ResourceNotFoundException; returned " + bagname.toString());
        } catch (ResourceNotFoundException ex) {
        }

    }

    @Test
    public void testFindHeadbagByPage() throws FileNotFoundException, DistributionException {
        s3Storage.setPageSize(2);
        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", s3Storage.findHeadBagFor("mds088kd2"));
        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", s3Storage.findHeadBagFor("mds013u4g"));

        assertEquals("mds013u4g.1_1.mbag0_4-8.7z", s3Storage.findHeadBagFor("mds013u4g", "1.1"));
        assertEquals("mds013u4g.1_0_1.mbag0_4-5.7z", s3Storage.findHeadBagFor("mds013u4g", "1.0.1"));
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z", s3Storage.findHeadBagFor("mds013u4g", "1.0.0"));

        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", s3Storage.findHeadBagFor("mds088kd2", "1.0.1"));
        assertEquals("mds088kd2.mbag0_3-14.7z", s3Storage.findHeadBagFor("mds088kd2", "0"));
        assertEquals("mds088kd2.mbag0_3-14.7z", s3Storage.findHeadBagFor("mds088kd2", "1"));

        try {
            String bagname = s3Storage.findHeadBagFor("mds013u4g9");
            fail("Failed to raise ResourceNotFoundException; returned " + bagname.toString());
        } catch (ResourceNotFoundException ex) {
        }

    }

}
