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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONException;

import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.StorageStateException;
import gov.nist.oar.distrib.ObjectNotFoundException;
import gov.nist.oar.distrib.cachemgr.CacheObject;

import io.findify.s3mock.S3Mock;

public class AWSS3CacheVolumeTest {

    private static Logger logger = LoggerFactory.getLogger(AWSS3CacheVolumeTest.class);

    static int port = 9001;
    static final String bucket = "oar-cv-test";
    static final String folder = "cach";
    static String hash = "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9";
    static S3Mock api = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
    static AmazonS3 s3client = null;
    AWSS3CacheVolume s3cv = null;
  
    @BeforeClass
    public static void setUpClass() throws IOException {
        String endpoint = "http://localhost:"+Integer.toString(port);
        api.start();
        s3client = AmazonS3ClientBuilder.standard()
                                        .withPathStyleAccessEnabled(true)  
                                        .withEndpointConfiguration(
                                                 new AwsClientBuilder.EndpointConfiguration(endpoint,
                                                                                            "us-east-1"))
                                        .withCredentials(new AWSStaticCredentialsProvider(
                                                                      new AnonymousAWSCredentials()))
                                        .build();
        
        if (s3client.doesBucketExistV2(bucket))
            destroyBucket();
        s3client.createBucket(bucket);

        // create folder
        ObjectMetadata md = new ObjectMetadata();
        md.setContentLength(0);
        InputStream mt = new ByteArrayInputStream(new byte[0]);
        try {
            s3client.putObject(bucket, folder+"/", mt, md);
        } finally {
            try { mt.close(); } catch (IOException ex) { }
        }
    }

    @Before
    public void setUp() throws IOException {
        String prefix = folder+"/";
        for (S3ObjectSummary os : s3client.listObjectsV2(bucket, prefix).getObjectSummaries())
            if (os.getKey().equals(prefix))
                prefix = null;
        assertNull(prefix);
        
        s3cv = new AWSS3CacheVolume(bucket, "cach", s3client);
    }

    @After
    public void tearDown() {
        s3cv = null;
        // delete contents of bucket
        depopulateFolder();
    }

    @AfterClass
    public static void tearDownClass() {
        destroyBucket();
        api.shutdown();
    }

    public static void destroyBucket() {
        List<S3ObjectSummary> files = s3client.listObjects(bucket).getObjectSummaries();
        for (S3ObjectSummary f : files) 
            s3client.deleteObject(bucket, f.getKey());
        s3client.deleteBucket(bucket);
    }

    public void depopulateFolder() throws AmazonServiceException {
        List<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<DeleteObjectsRequest.KeyVersion>();
        String prefix = folder+"/";
        for (S3ObjectSummary os : s3client.listObjectsV2(bucket, prefix).getObjectSummaries()) {
            if (! os.getKey().equals(prefix))
                keys.add(new DeleteObjectsRequest.KeyVersion(os.getKey()));
        }
        DeleteObjectsRequest dor = new DeleteObjectsRequest(bucket).withKeys(keys);
        s3client.deleteObjects(dor);
    }

    @Test
    public void testCtor() {
        assertEquals(s3cv.bucket, bucket);
        assertEquals(s3cv.folder, "cach");
        assertEquals(s3cv.name, "s3://oar-cv-test/cach/");
        assertEquals(s3cv.getName(), "s3://oar-cv-test/cach/");

        // assertTrue(! s3client.doesObjectExist(bucket, folder+"/goob/gurn"));
    }

    @Test
    public void testEnsureFolder() throws AmazonServiceException {
        String subdir = folder+"/goob";
        assertTrue(! s3client.doesObjectExist(bucket, subdir+"/"));
        assertTrue(AWSS3CacheVolume.ensureBucketFolder(s3client, bucket, subdir));
        assertTrue(s3client.doesObjectExist(bucket, subdir+"/"));

        String subobj = subdir+"/gurn";
        ObjectMetadata md = new ObjectMetadata();
        byte[] obj = "1".getBytes();
        md.setContentLength(obj.length);
        InputStream is = new ByteArrayInputStream(obj);
        try {
            s3client.putObject(bucket, subobj, is, md);
        } finally {
            try { is.close(); } catch (IOException ex) { }
        }
        assertTrue(s3client.doesObjectExist(bucket, subobj));

        assertTrue(! AWSS3CacheVolume.ensureBucketFolder(s3client, bucket, subdir));
        assertTrue(s3client.doesObjectExist(bucket, subdir+"/"));
        assertTrue(s3client.doesObjectExist(bucket, subobj));
    }

    @Test
    public void testExists() throws StorageVolumeException {
        String objname = String.format("%s/goob", folder);
        assertTrue(! s3cv.exists("goob"));
        
        ObjectMetadata md = new ObjectMetadata();
        byte[] obj = "1".getBytes();
        md.setContentLength(obj.length);
        InputStream is = new ByteArrayInputStream(obj);
        try {
            s3client.putObject(bucket, objname, is, md);
        } finally {
            try { is.close(); } catch (IOException ex) { }
        }
        assertTrue(s3client.doesObjectExist(bucket, objname));
        assertTrue(s3cv.exists("goob"));

        s3cv.remove("goob");
        assertTrue(! s3client.doesObjectExist(bucket, objname));
        assertTrue(! s3cv.exists("goob"));
    }

    @Test
    public void testSaveAs() throws StorageVolumeException {
        String objname = folder + "/test.txt";
        assertTrue(! s3client.doesObjectExist(bucket, objname));
        assertTrue(! s3cv.exists("test.txt"));

        byte[] obj = "hello world.\n".getBytes();
        JSONObject md = new JSONObject();
        md.put("size", obj.length);
        md.put("contentType", "text/plain");
        InputStream is = new ByteArrayInputStream(obj);

        try {
            s3cv.saveAs(is, "test.txt", md);
        } finally {
            try { is.close(); } catch (IOException ex) { }
        }
        assertTrue(s3client.doesObjectExist(bucket, objname));
        assertTrue(s3cv.exists("test.txt"));
    }

    @Test
    public void testGetStream() throws StorageVolumeException, IOException {
        String objname = folder + "/test.txt";
        assertTrue(! s3client.doesObjectExist(bucket, objname));

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
        assertTrue(! s3client.doesObjectExist(bucket, objname));
        assertTrue(! s3cv.exists("test.txt"));
    }

    @Test
    public void getSaveObject() throws StorageVolumeException {
        String objname1 = folder + "/test.txt";
        String objname2 = folder + "/gurn.txt";
        assertTrue(! s3client.doesObjectExist(bucket, objname1));
        assertTrue(! s3client.doesObjectExist(bucket, objname2));

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
        assertEquals(co.volname, "s3://oar-cv-test/cach/");
        assertNull(co.id);
        assertEquals(co.score, 0.0, 0.0);

        s3cv.saveAs(co, "gurn.txt");
        assertTrue(s3client.doesObjectExist(bucket, objname1));
        assertTrue(s3client.doesObjectExist(bucket, objname2));
    }
}
