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

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class AWSS3ClientProviderTest {

    // static S3MockApplication mockServer = null;
    @ClassRule
    public static S3MockTestRule siterule = new S3MockTestRule();

    AWSS3ClientProvider s3 = null;
    static final String bucket = "oar-lts-test";
    
    @BeforeClass
    public static void setUpClass() throws IOException {
        // mockServer = S3MockApplication.start();  // http: port=9090
        AWSS3ClientProvider s3 = createS3Provider();

        AmazonS3 s3client = s3.client();
        if (s3client.doesBucketExistV2(bucket))
            destroyBucket();
        s3client.createBucket(bucket);
        // populateBucket(s3client);
    }

    public static AWSS3ClientProvider createS3Provider() {
        // import credentials from the EC2 machine we are running on
        final BasicAWSCredentials credentials = new BasicAWSCredentials("foo", "bar");
        final String endpoint = "http://localhost:9090/";
        final String region = "us-east-1";

        return new AWSS3ClientProvider(new AWSStaticCredentialsProvider(credentials), region, 2, endpoint);
    }

    @AfterClass
    public static void tearDownClass() {
        destroyBucket();
        // mockServer.stop();
    }

    public static void destroyBucket() {
        AWSS3ClientProvider s3 = createS3Provider();
        AmazonS3 s3client = s3.client();
        List<S3ObjectSummary> files = s3client.listObjects(bucket).getObjectSummaries();
        for (S3ObjectSummary f : files) 
            s3client.deleteObject(bucket, f.getKey());
        s3client.deleteBucket(bucket);
    }

    @Before
    public void setUp() {
        s3 = createS3Provider();
    }

    @Test
    public void testClient() {
        assertNotNull(s3);
        assertEquals(2, s3.accessesLeft());

        AmazonS3 cli = s3.client();
        assertNotNull(cli);
        assertEquals(1, s3.accessesLeft());

        AmazonS3 cli2 = s3.client();
        assertNotNull(cli2);
        assertEquals(cli, cli2);
        assertEquals(0, s3.accessesLeft());

        cli2 = s3.client();
        assertNotNull(cli2);
        assertNotEquals(cli, cli2);
        assertEquals(1, s3.accessesLeft());

        // make sure the original is still usable
        assertTrue(cli.doesBucketExistV2(bucket));
    }

    @Test
    public void testShutdown() {
        AmazonS3 cli = s3.client();
        assertNotNull(cli);
        assertEquals(1, s3.accessesLeft());

        s3.shutdown();
        assertEquals(0, s3.accessesLeft());

        try {
            cli.doesBucketExistV2(bucket);
            fail("Failed to fail on disabled client");
        } catch (IllegalStateException ex) {
            // okay!
        }

        cli = s3.client();
        assertEquals(1, s3.accessesLeft());
        assertTrue(cli.doesBucketExistV2(bucket));
    }

    @Test
    public void testClone() {
        assertNotNull(s3);
        assertEquals(2, s3.accessesLeft());

        AmazonS3 cli = s3.client();
        assertNotNull(cli);
        assertEquals(1, s3.accessesLeft());

        AWSS3ClientProvider s32 = s3.cloneMe();
        assertNotEquals(s3, s32);
        assertEquals(2, s32.accessesLeft());
        AmazonS3 cli2 = s32.client();
        assertNotNull(cli2);
        assertNotEquals(cli, cli2);
        assertEquals(1, s3.accessesLeft());
    }
}
