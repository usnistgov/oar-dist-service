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
package gov.nist.oar.distrib.cachemgr.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
// import io.findify.s3mock.S3Mock;
import gov.nist.oar.distrib.storage.S3MockTestRule;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

public class AWSS3CacheVolumeTest {

    // static S3Mock api = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
    @ClassRule
    public static S3MockTestRule siterule = new S3MockTestRule();

    // private static Logger logger = LoggerFactory.getLogger(AWSS3CacheVolumeTest.class);

    static int port = 9001;
    static final String bucket = "oar-cv-test";
    static final String folder = "cach";
    static String hash = "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9";
    static S3Client s3client = null;
    AWSS3CacheVolume s3cv = null;
  
    @BeforeClass
    public static void setUpClass() {
        s3client = createS3Client();

        // Check if bucket exists and destroy it if necessary
        if (bucketExists(bucket)) {
            destroyBucket();
        }

        // Create the bucket
        s3client.createBucket(CreateBucketRequest.builder()
                .bucket(bucket)
                .build());

        // Create folder (zero-length object with a trailing slash)
        String folderKey = folder + "/";
        try (InputStream emptyContent = new ByteArrayInputStream(new byte[0])) {
            s3client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(folderKey)
                            .contentLength(0L)
                            .contentType("application/x-directory")
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromInputStream(emptyContent, 0L));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create folder in bucket", ex);
        }
    }

    public static S3Client createS3Client() {
        final AwsBasicCredentials credentials = AwsBasicCredentials.create("foo", "bar");
        final String endpoint = "http://localhost:9090/";
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("us-east-1"))
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true) // Required for S3Mock
                .build();
    }

    private static boolean bucketExists(String bucketName) {
        try {
            s3client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    private static void destroyBucket() {
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

    @Before
    public void setUp() {
        // Verify folder exists in the bucket
        String folderKey = folder + "/";
        ListObjectsV2Response response = s3client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(folderKey)
                .build());

        boolean folderExists = response.contents().stream()
                .anyMatch(object -> object.key().equals(folderKey));
        assertNull("Folder does not exist: " + folder, folderExists ? null : folderKey);

        // Initialize AWSS3CacheVolume
        try {
            s3cv = new AWSS3CacheVolume(bucket, folder, s3client);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize AWSS3CacheVolume", e);
        }
    }

    @After
    public void tearDown() {
        s3cv = null;
        depopulateFolder();
    }

    private void depopulateFolder() {
        String folderKey = folder + "/";
        List<String> keysToDelete = s3client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(folderKey)
                        .build())
                .contents()
                .stream()
                .filter(obj -> !obj.key().equals(folderKey)) // Skip the folder itself
                .map(S3Object::key)
                .collect(Collectors.toList());

        if (!keysToDelete.isEmpty()) {
            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder()
                            .objects(keysToDelete.stream()
                                    .map(key -> ObjectIdentifier.builder().key(key).build())
                                    .collect(Collectors.toList()))
                            .build())
                    .build();

            s3client.deleteObjects(deleteRequest);
        }
    }

    @Test
    public void testCtor() throws FileNotFoundException {
        assertEquals(s3cv.bucket, bucket);
        assertEquals(s3cv.folder, "cach");
        assertEquals(s3cv.name, "s3:/oar-cv-test/cach/");
        assertEquals(s3cv.getName(), "s3:/oar-cv-test/cach/");

        s3cv = new AWSS3CacheVolume(bucket, "cach", "goober", s3client);
        assertEquals(s3cv.bucket, bucket);
        assertEquals(s3cv.folder, "cach");
        assertEquals(s3cv.name, "goober");
        assertEquals(s3cv.getName(), "goober");

        // assertTrue(! s3client.doesObjectExist(bucket, folder+"/goob/gurn"));
    }

    @Test
    public void testEnsureFolder() {
        String subdir = folder + "/goob";
        String folderKey = subdir + "/";

        // Ensure the folder doesn't exist initially
        assertTrue(!folderExists(bucket, folderKey));
        assertTrue(AWSS3CacheVolume.ensureBucketFolder(s3client, bucket, subdir));
        assertTrue(folderExists(bucket, folderKey));

        // Add an object to the folder
        String subobj = subdir + "/gurn";
        byte[] obj = "1".getBytes();

        try (InputStream is = new ByteArrayInputStream(obj)) {
            s3client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(subobj)
                            .contentLength((long) obj.length)
                            .build(),
                    RequestBody.fromInputStream(is, obj.length));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to upload object", ex);
        }

        assertTrue(objectExists(bucket, subobj));

        // Ensuring the folder again should return false
        assertTrue(!AWSS3CacheVolume.ensureBucketFolder(s3client, bucket, subdir));
        assertTrue(folderExists(bucket, folderKey));
        assertTrue(objectExists(bucket, subobj));
    }

    @Test
    public void testExists() throws StorageVolumeException {
        String objname = String.format("%s/goob", folder);
        assertTrue(!s3cv.exists("goob"));

        byte[] obj = "1".getBytes();
        try (InputStream is = new ByteArrayInputStream(obj)) {
            s3client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objname)
                            .contentLength((long) obj.length)
                            .build(),
                    RequestBody.fromInputStream(is, obj.length));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to upload object", ex);
        }

        assertTrue(objectExists(bucket, objname));
        assertTrue(s3cv.exists("goob"));

        // Remove the object
        s3cv.remove("goob");
        assertTrue(!objectExists(bucket, objname));
        assertTrue(!s3cv.exists("goob"));
    }

    @Test
    public void testSaveAs() throws StorageVolumeException {
        String objname = folder + "/test.txt";
        assertTrue(!objectExists(bucket, objname));
        assertTrue(!s3cv.exists("test.txt"));

        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", obj.length);
        md.put("contentType", "text/plain");

        try (InputStream is = new ByteArrayInputStream(obj)) {
            s3cv.saveAs(is, "test.txt", md);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to upload object", ex);
        }

        assertTrue(objectExists(bucket, objname));
        assertTrue(s3cv.exists("test.txt"));
        assertTrue("metadata not updated with 'modified'", md.has("modified"));

        long mod = md.getLong("modified");
        assertTrue("Bad mod date: " + Long.toString(mod), mod > 0L);
        String vcs = md.getString("volumeChecksum");
        assertTrue("Bad volume checksum: " + vcs,
                vcs.startsWith("etag ") && vcs.length() > 36);
    }

    // Helper method to check if an object exists
    private boolean objectExists(String bucket, String key) {
        try {
            s3client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    // Helper method to check if a folder exists
    private boolean folderExists(String bucket, String folderKey) {
        ListObjectsV2Response response = s3client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(folderKey)
                .build());
        return response.contents().stream()
                .anyMatch(object -> object.key().equals(folderKey));
    }

    @Test
    public void testGet() throws StorageVolumeException {
        byte[] obj = "hello world.\n".getBytes();
        testSaveAs();
        CacheObject co = s3cv.get("test.txt");
        assertEquals(co.getSize(), obj.length);
        long mod = co.getLastModified();
        assertTrue("Bad mod time: "+Long.toString(mod), mod > 0L);
        assertEquals(co.getMetadatumString("contentType",""), "text/plain");
    }

    @Test
    public void testSaveAsWithMD5() throws StorageVolumeException {
        String objname = folder + "/test.txt";
        assertTrue(!objectExists(bucket, objname));
        assertTrue(!s3cv.exists("test.txt"));

        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", obj.length);
        md.put("contentType", "text/plain");
        md.put("contentMD5", "JjJWGp65Tg0F4+AyzFre7Q==");
        InputStream is = new ByteArrayInputStream(obj);

        try {
            s3cv.saveAs(is, "test.txt", md);
        } finally {
            try { is.close(); } catch (IOException ex) { }
        }
        assertTrue(objectExists(bucket, objname));
        assertTrue(s3cv.exists("test.txt"));
        assertEquals(md.getString("contentMD5"), "JjJWGp65Tg0F4+AyzFre7Q==");

        // the etag should be an MD5 sum, but for some reason it is not
        // assertEquals(md.getString("volumeChecksum"), "etag JjJWGp65Tg0F4+AyzFre7Q==");
    }

    /*
     * this test is unreliable with S3Mock
     *
    @Test
    public void testSaveAsWithBadSize() throws StorageVolumeException {
        String objname = folder + "/test.txt";
        assertTrue(! s3client.doesObjectExist(bucket, objname));
        assertTrue(! s3cv.exists("test.txt"));

        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", 5);
        md.put("contentType", "text/plain");
        InputStream is = new ByteArrayInputStream(obj);

        try {
            s3cv.saveAs(is, "test.txt", md);
            fail("Failed to detect bad size");
        } catch (StorageVolumeException ex) {
            // Expected!
            assertTrue("Failed for the wrong reason: "+ex.getMessage(),
                       ex.getMessage().contains("correct number of bytes"));
        } finally {
            try { is.close(); } catch (IOException ex) { }
        }
        assertTrue(! s3client.doesObjectExist(bucket, objname));
        //
        // NOTE!  That this assert sometimes fails is believed to be an issue with S3Mock
        // assertTrue(! s3cv.exists("test.txt"));
    }
    */

    /*
     * S3Mock apparently does not check contentMD5 values
     *
     */
    @Test
    public void testSaveAsWithBadMD5() throws StorageVolumeException {
        String objname = folder + "/test.txt";
        assertTrue(!objectExists(bucket, objname));
        assertTrue(!s3cv.exists("test.txt"));

        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", obj.length);
        md.put("contentType", "text/plain");
        md.put("contentMD5", "goob");
        InputStream is = new ByteArrayInputStream(obj);

        try {
            s3cv.saveAs(is, "test.txt", md);
            fail("Failed to detect bad MD5 sum");
        } catch (StorageVolumeException ex) {
            // Expected!
            assertTrue("Failed for the wrong reason: "+ex.getMessage(),
                       ex.getMessage().contains("md5 transfer"));
        } finally {
            try { is.close(); } catch (IOException ex) { }
        }
        assertTrue("Failed transfered object not deleted from bucket",
                   !objectExists(bucket, objname));
        assertTrue("Failed transfered object not deleted from volume",
                   !s3cv.exists("test.txt"));
    }

    @Test
    public void testGetStream() throws StorageVolumeException, IOException {
        String objname = folder + "/test.txt";
        assertTrue(!objectExists(bucket, objname));

        try {
            s3cv.getStream("test.txt");
            fail("Missing object did not throw ObjectNotFoundException");
        } catch (ObjectNotFoundException ex) { }

        testSaveAs();
        InputStream is = s3cv.getStream("test.txt");
        assertNotNull(is);
        BufferedReader rdr = new BufferedReader(new InputStreamReader(is));
        try {
            assertEquals(rdr.readLine(), "hello world.");
            assertNull(rdr.readLine());
        }
        finally {
            try { rdr.close(); } catch (IOException ex) { }
        }

        s3cv.remove("test.txt");
        assertTrue(!objectExists(bucket, objname));
        assertTrue(!s3cv.exists("test.txt"));
    }

    @Test
    public void getSaveObject() throws StorageVolumeException {
        String objname1 = folder + "/test.txt";
        String objname2 = folder + "/gurn.txt";
        assertTrue(!objectExists(bucket, objname1));
        assertTrue(!objectExists(bucket, objname2));

        try {
            s3cv.get("test.txt");
            fail("Missing object did not throw ObjectNotFoundException");
        } catch (ObjectNotFoundException ex) { }

        testSaveAs();
        CacheObject co = s3cv.get("test.txt");
        assertNotNull(co);
        assertEquals(co.getSize(), 13);
        assertEquals(co.getMetadatumString("contentType", null), "text/plain");
        assertEquals(co.volume, s3cv);
        assertEquals(co.volname, "s3:/oar-cv-test/cach/");
        assertNull(co.id);
        assertEquals(co.score, 0.0, 0.0);

        s3cv.saveAs(co, "gurn.txt");
        assertTrue(objectExists(bucket, objname1));
        assertTrue(objectExists(bucket, objname2));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRedirectForUnsupported()
        throws StorageVolumeException, UnsupportedOperationException, IOException
    {
        s3cv.getRedirectFor("goober");
    }

    @Test
    public void testRedirectFor()
        throws StorageVolumeException, UnsupportedOperationException, IOException, MalformedURLException
    {
        s3cv = new AWSS3CacheVolume(bucket, "cach", s3client, "https://ex.org/");
        assertEquals(new URL("https://ex.org/goober"), s3cv.getRedirectFor("goober"));
        assertEquals(new URL("https://ex.org/i%20a/m%20groot"), s3cv.getRedirectFor("i a/m groot"));
    }

    @Test
    public void testRedirectFor2()
        throws StorageVolumeException, UnsupportedOperationException, IOException, MalformedURLException
    {
        s3cv = new AWSS3CacheVolume(bucket, "cach", s3client, "https://ex.org/");
        testSaveAs();
        String burl = "http://localhost:9090//"+bucket+"/"+folder+"/";
        assertEquals(new URL(burl+"test.txt"), s3cv.getRedirectFor("test.txt"));
    }
}
