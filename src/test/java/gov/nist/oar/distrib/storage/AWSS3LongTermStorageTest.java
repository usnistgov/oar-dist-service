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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.bags.preservation.BagUtils;

import cloud.localstack.LocalstackTestRunner;
import cloud.localstack.TestUtils;

/**
 * This is test class is used to connect to long term storage on AWS S3
 * To test AWSS3LongTermStorage
 *
 * @author Deoyani Nandrekar-Heinis
 */
@RunWith(LocalstackTestRunner.class)
public class AWSS3LongTermStorageTest {
  
    private static Logger logger = LoggerFactory.getLogger(FilesystemLongTermStorageTest.class);

    static final String bucket = "oar-lts-test";
    static String hash = "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9";
    static AmazonS3 s3client = TestUtils.getClientS3();
    AWSS3LongTermStorage s3Storage = null;
  
    @BeforeClass
    public static void setUpClass() throws IOException {
        if (s3client.doesBucketExistV2(bucket))
            destroyBucket();
        s3client.createBucket(bucket);
        populateBucket();
    }

    @Before
    public void setUp() throws IOException {
        s3Storage = new AWSS3LongTermStorage(bucket, s3client);
    }

    @After
    public void tearDown() {
        s3Storage = null;
    }

    @AfterClass
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
            for(int i=0; i < 3; i++) {
                String bag = base + Integer.toString(j++) + ((i > 1) ? ".7z" : ".zip");
                String baghash = hash+" "+bag;

                if (! s3client.doesObjectExist(bucket, bag)) {
                    ObjectMetadata md = new ObjectMetadata();
                    md.setContentLength(1);
                    // md.setContentMD5("1B2M2Y8AsgTpgAmY7PhCfg==");
                    md.setContentType("text/plain");
                    try (InputStream ds = new ByteArrayInputStream("0".getBytes())) {
                        s3client.putObject(bucket, bag, ds, md);
                    }

                    md.setContentLength(baghash.length());
                    // md.setContentMD5(null);
                    try (InputStream ds = new ByteArrayInputStream(baghash.getBytes())) {
                        s3client.putObject(bucket, bag+".sha256", ds, md);
                    }
                }
            }
        }
        
    }

    @Test
    public void testCtor() throws FileNotFoundException {
        assert(s3client.doesBucketExistV2(bucket));
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
            fail("Failed to raise ResourceNotFoundException; returned "+filenames.toString());
        } catch (ResourceNotFoundException ex) { }

        filenames =  new ArrayList<String>();
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
    public void testFileChecksum() throws FileNotFoundException, DistributionException  {
        String getChecksumHash = s3Storage.getChecksum("mds088kd2.mbag0_3-10.zip").hash;
        assertEquals(getChecksumHash.trim(), hash.trim());

        String h = "6f6173bf926eef7978d86a98f19ebc54b14ce3f8acaa2ce7dc8d199ae65adcb7";
        getChecksumHash = s3Storage.getChecksum("mds088kd2.mbag0_3-10.zip.sha256").hash;
        assertEquals(h.trim(), getChecksumHash.trim());
    }

    @Test
    public void testFileSize() throws FileNotFoundException, DistributionException  {
        long filelength = s3Storage.getSize("mds088kd2.1_0_1.mbag0_4-17.7z");
        assertEquals(1, filelength);

        filelength = s3Storage.getSize("mds088kd2.1_0_1.mbag0_4-17.7z.sha256");
        assertEquals(94, filelength);
    } 

    //Need to update deatils to compare two file streams
    @Test
    public void testFileStream() throws FileNotFoundException, DistributionException, IOException  {
        InputStream is = s3Storage.openFile("mds088kd2.1_0_1.mbag0_4-17.7z");
        byte[] buf = new byte[100];
        int n = is.read(buf);
        assertEquals("Unexpected output: "+(new String(buf, 0, n)), 1, n);
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
        } catch (FileNotFoundException ex) { }
    } 

    @Test
    public void testFileHeadbag() throws FileNotFoundException, DistributionException {
        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", s3Storage.findHeadBagFor("mds088kd2")); 
        assertEquals("mds013u4g.1_1.mbag0_4-8.7z",    s3Storage.findHeadBagFor("mds013u4g"));

        assertEquals("mds013u4g.1_1.mbag0_4-8.7z",    s3Storage.findHeadBagFor("mds013u4g", "1.1"));
        assertEquals("mds013u4g.1_0_1.mbag0_4-5.7z",  s3Storage.findHeadBagFor("mds013u4g", "1.0.1"));
        assertEquals("mds013u4g.1_0_0.mbag0_4-2.7z",  s3Storage.findHeadBagFor("mds013u4g", "1.0.0"));

        assertEquals("mds088kd2.1_0_1.mbag0_4-17.7z", s3Storage.findHeadBagFor("mds088kd2", "1.0.1")); 
        assertEquals("mds088kd2.mbag0_3-14.7z", s3Storage.findHeadBagFor("mds088kd2", "0")); 
        assertEquals("mds088kd2.mbag0_3-14.7z", s3Storage.findHeadBagFor("mds088kd2", "1")); 

        try {
            String bagname = s3Storage.findHeadBagFor("mds013u4g9");
            fail("Failed to raise ResourceNotFoundException; returned "+bagname.toString());
        } catch (ResourceNotFoundException ex) { }

    }
}
