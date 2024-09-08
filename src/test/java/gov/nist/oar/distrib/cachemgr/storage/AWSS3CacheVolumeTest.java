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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;

public class AWSS3CacheVolumeTest {

    static int port = 9001;
    static final String bucket = "oar-cv-test";
    static final String folder = "cach";
    static AmazonS3 s3client = null;
    AWSS3CacheVolume s3cv = null;

    @BeforeAll
    public static void setUpClass() throws IOException {
        s3client = createS3Client();
        if (s3client.doesBucketExistV2(bucket))
            destroyBucket();
        s3client.createBucket(bucket);

        // create folder
        ObjectMetadata md = new ObjectMetadata();
        md.setContentLength(0);
        try (InputStream mt = new ByteArrayInputStream(new byte[0])) {
            s3client.putObject(bucket, folder + "/", mt, md);
        }
    }

    public static AmazonS3 createS3Client() {
        // Create an S3 client (stubbed, or use mock for real use case)
        return null; // Actual implementation should create an AmazonS3 client
    }

    @BeforeEach
    public void setUp() throws IOException {
        // Confirm the bucket folder exists
        String prefix = folder;
        for (S3ObjectSummary os : s3client.listObjectsV2(bucket, prefix).getObjectSummaries()) {
            if (os.getKey().equals(prefix + "/")) {
                prefix = null; // Folder found
            }
        }
        assertNull(prefix);

        s3cv = new AWSS3CacheVolume(bucket, "cach", s3client);
    }

    @AfterEach
    public void tearDown() {
        s3cv = null;
        depopulateFolder();
    }

    @AfterAll
    public static void tearDownClass() {
        destroyBucket();
    }

    public static void destroyBucket() {
        List<S3ObjectSummary> files = s3client.listObjects(bucket).getObjectSummaries();
        for (S3ObjectSummary f : files) {
            s3client.deleteObject(bucket, f.getKey());
        }
        s3client.deleteBucket(bucket);
    }

    public void depopulateFolder() throws AmazonServiceException {
        List<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
        String prefix = folder + "/";
        for (S3ObjectSummary os : s3client.listObjectsV2(bucket, prefix).getObjectSummaries()) {
            if (!os.getKey().equals(prefix)) {
                keys.add(new DeleteObjectsRequest.KeyVersion(os.getKey()));
            }
        }
        if (!keys.isEmpty()) {
            s3client.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys));
        }
    }

    @Test
    public void testCtor() {
        assertEquals(bucket, s3cv.bucket);
        assertEquals("cach", s3cv.folder);
        assertEquals("s3:/oar-cv-test/cach/", s3cv.getName());
    }

    @Test
    public void testEnsureFolder() {
        String subdir = folder + "/goob";
        assertTrue(!s3client.doesObjectExist(bucket, subdir + "/"));
        assertTrue(AWSS3CacheVolume.ensureBucketFolder(s3client, bucket, subdir));
        assertTrue(s3client.doesObjectExist(bucket, subdir + "/"));
    }

    @Test
    public void testExists() throws StorageVolumeException {
        String objname = folder + "/goob";
        assertTrue(!s3cv.exists("goob"));

        // Simulate object creation in S3
        s3cv.remove("goob");
        assertTrue(!s3client.doesObjectExist(bucket, objname));
    }

    @Test
    public void testSaveAs() throws StorageVolumeException {
        String objname = folder + "/test.txt";
        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", obj.length);
        md.put("contentType", "text/plain");

        try (InputStream is = new ByteArrayInputStream(obj)) {
            s3cv.saveAs(is, "test.txt", md);
        } catch (IOException ex) {
            fail("Failed to save object");
        }

        assertTrue(s3client.doesObjectExist(bucket, objname));
        assertTrue(s3cv.exists("test.txt"));
    }

    @Test
    public void testSaveAsWithBadMD5() {
        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", obj.length);
        md.put("contentType", "text/plain");
        md.put("contentMD5", "goob");

        assertThrows(StorageVolumeException.class, () -> {
            try (InputStream is = new ByteArrayInputStream(obj)) {
                s3cv.saveAs(is, "test.txt", md);
            }
        });
    }

    @Test
    public void testGetStream() throws StorageVolumeException, IOException {
        assertThrows(ObjectNotFoundException.class, () -> s3cv.getStream("test.txt"));
        testSaveAs();
        try (InputStream is = s3cv.getStream("test.txt");
             BufferedReader rdr = new BufferedReader(new InputStreamReader(is))) {
            assertEquals("hello world.", rdr.readLine());
            assertNull(rdr.readLine());
        }
    }

    @Test
    public void testRedirectFor() throws StorageVolumeException, URISyntaxException, IOException , MalformedURLException {
        s3cv = new AWSS3CacheVolume(bucket, "cach", s3client, "https://ex.org/");
        assertEquals(new URI("https://ex.org/goober").toURL(), s3cv.getRedirectFor("goober"));
        assertEquals(new URI("https://ex.org/i%20a/m%20groot").toURL(), s3cv.getRedirectFor("i a/m groot"));
    }

    @Test
    public void testRedirectFor2() throws StorageVolumeException, URISyntaxException, IOException, MalformedURLException {
        s3cv = new AWSS3CacheVolume(bucket, "cach", s3client, "https://ex.org/");
        testSaveAs();
        String burl = "http://localhost:9090//" + bucket + "/" + folder + "/";
        assertEquals(new URI(burl + "test.txt").toURL(), s3cv.getRedirectFor("test.txt"));
    }
}
