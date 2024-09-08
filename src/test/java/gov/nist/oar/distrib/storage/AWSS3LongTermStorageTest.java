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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;

public class AWSS3LongTermStorageTest {

    static AmazonS3 s3client = null;
    static final String bucket = "oar-lts-test";
    static String hash = "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9";
    AWSS3LongTermStorage s3Storage = null;

    @BeforeAll
    public static void setUpClass() throws IOException {
        s3client = createS3Client();

        if (s3client.doesBucketExistV2(bucket))
            destroyBucket();
        s3client.createBucket(bucket);
        populateBucket();
    }

    public static AmazonS3 createS3Client() {
        final BasicAWSCredentials credentials = new BasicAWSCredentials("foo", "bar");
        final String endpoint = "http://localhost:9090/";
        final String region = "us-east-1";
        EndpointConfiguration epconfig = new EndpointConfiguration(endpoint, region);

        return AmazonS3Client.builder()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(epconfig)
                .enablePathStyleAccess()
                .build();
    }

    @BeforeEach
    public void setUp() throws IOException {
        s3Storage = new AWSS3LongTermStorage(bucket, s3client);
    }

    @AfterEach
    public void tearDown() {
        s3Storage = null;
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

    public static void populateBucket() throws IOException {
        String[] bases = {
                "mds013u4g.1_0_0.mbag0_4-", "mds013u4g.1_0_1.mbag0_4-", "mds013u4g.1_1.mbag0_4-",
                "mds088kd2.mbag0_3-", "mds088kd2.mbag0_3-", "mds088kd2.1_0_1.mbag0_4-"
        };

        int j = 0;
        for (String base : bases) {
            for (int i = 0; i < 3; i++) {
                String bag = base + Integer.toString(j++) + ((i > 1) ? ".7z" : ".zip");
                String baghash = hash + " " + bag;

                if (!s3client.doesObjectExist(bucket, bag)) {
                    ObjectMetadata md = new ObjectMetadata();
                    md.setContentLength(1);
                    md.setContentType("text/plain");
                    try (InputStream ds = new ByteArrayInputStream("0".getBytes())) {
                        s3client.putObject(bucket, bag, ds, md);
                    }

                    md.setContentLength(baghash.length());
                    try (InputStream ds = new ByteArrayInputStream(baghash.getBytes())) {
                        s3client.putObject(bucket, bag + ".sha256", ds, md);
                    }
                }
            }
        }
    }

    @Test
    public void testCtor() throws FileNotFoundException {
        assertTrue(s3client.doesBucketExistV2(bucket));
    }

    @Test
    public void testFindBagsFor() throws DistributionException, FileNotFoundException {
        List<String> filenames = new ArrayList<>();
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
            fail("Failed to raise ResourceNotFoundException; returned " + filenames);
        } catch (ResourceNotFoundException ex) {
            // expected
        }

        filenames = new ArrayList<>();
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
    public void testFileChecksum() throws FileNotFoundException, DistributionException {
        String getChecksumHash = s3Storage.getChecksum("mds088kd2.mbag0_3-10.zip").hash;
        assertEquals(getChecksumHash.trim(), hash.trim());

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

    @Test
    public void testFileStream() throws FileNotFoundException, DistributionException, IOException {
        InputStream is = s3Storage.openFile("mds088kd2.1_0_1.mbag0_4-17.7z");
        byte[] buf = new byte[100];
        int n = is.read(buf);
        assertEquals(1, n, "Unexpected output: " + (new String(buf, 0, n)));
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
            // expected
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
            fail("Failed to raise ResourceNotFoundException; returned " + bagname);
        } catch (ResourceNotFoundException ex) {
            // expected
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
            fail("Failed to raise ResourceNotFoundException; returned " + bagname);
        } catch (ResourceNotFoundException ex) {
            // expected
        }
    }
}
