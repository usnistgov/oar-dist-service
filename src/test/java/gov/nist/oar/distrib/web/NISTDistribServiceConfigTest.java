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
 */
package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.FileSystemUtils;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.File;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = NISTDistribServiceConfig.class)
@TestPropertySource(properties = {
        "distrib.bagstore.mode=local",
        "distrib.bagstore.location=${basedir}/src/test/resources",
        "distrib.packaging.maxpackagesize = 100000",
        "distrib.packaging.maxfilecount = 2",
        "distrib.packaging.allowedurls = nist.gov|s3.amazonaws.com/nist-midas",
        "distrib.baseurl=http://localhost/od/ds",
        "distrib.cachemgr.admindir=${java.io.tmpdir}/testcmgr",
        "distrib.cachemgr.headbagCacheSize=40000000",
        "distrib.cachemgr.volumes[0].location=file://vols/king",
        "distrib.cachemgr.volumes[0].name=king",
        "distrib.cachemgr.volumes[0].capacity=30000000",
        "distrib.cachemgr.volumes[1].location=file://vols/pratt",
        "distrib.cachemgr.volumes[1].name=pratt",
        "distrib.cachemgr.volumes[1].capacity=36000000",
        "distrib.cachemgr.volumes[0].roles[0]=small",
        "distrib.cachemgr.volumes[0].roles[1]=fast",
        "distrib.cachemgr.volumes[1].roles[0]=large",
        "distrib.cachemgr.volumes[1].roles[1]=general",
        "logging.path=${basedir}/logs"
})
public class NISTDistribServiceConfigTest {

    static File testdir = null;

    @Autowired
    NISTDistribServiceConfig config;

    @Autowired
    CacheManagerProvider provider;

    public static void cleanTestDir(File testdir) throws IOException {
        /*
        */
        if (testdir.exists()) 
            FileSystemUtils.deleteRecursively(testdir);
        testdir.mkdirs();
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir == null)
            throw new RuntimeException("java.io.tmpdir property not set");
        File tmp = new File(tmpdir);
        if (! tmp.exists())
            tmp.mkdir();
        testdir = new File(tmp, "testcmgr");
        cleanTestDir(testdir);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        testdir.delete();
    }

    @After
    public void tearDown() throws IOException {
        cleanTestDir(testdir);
    }

    @Test
    public void testInjection() {
        assertEquals("local", config.mode);
        assertTrue(config.bagstore.endsWith("src/test/resources"));
        assertNotNull(config.mimemap);
        assertNotNull(config.lts);
        assertTrue(config.lts instanceof FilesystemLongTermStorage);
        assertTrue(provider.canProvideManager());

        File tst = new File(testdir, "data.sqlite");
        assertTrue(tst.isFile());
        File cvroot = new File(testdir, "vols");
        assertTrue(cvroot.isDirectory());
        tst = new File(cvroot, "king");
        assertTrue(tst.isDirectory());
        tst = new File(cvroot, "pratt");
        assertTrue(tst.isDirectory());
        tst = new File(testdir, "headbags");
        assertTrue(tst.isDirectory());
        tst = new File(testdir, "headbags/inventory.sqlite");
        assertTrue(tst.isFile());
        tst = new File(testdir, "headbags/cv0");
        assertTrue(tst.isDirectory());
        tst = new File(testdir, "headbags/cv1");
        assertTrue(tst.isDirectory());
    }
}
