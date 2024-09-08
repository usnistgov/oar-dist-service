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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.FileDescription;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.VolumeConfig;
import gov.nist.oar.distrib.cachemgr.inventory.ChecksumCheck;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagDB;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.pdr.PDRStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;

public class CacheEnabledFileDownloadServiceTest {

    @TempDir
    Path tempDir;

    static Path tmpltsroot = null;

    final String ltsdir = System.getProperty("project.test.resourceDirectory");
    String mtltsdir = null;
    PDRDatasetRestorer rstr = null;
    ConfigurableCache cache = null;
    HeadBagCacheManager hbcm = null;
    PDRCacheManager mgr = null;

    FilesystemLongTermStorage lts = null;
    CacheEnabledFileDownloadService svc = null;

    HeadBagCacheManager createHBCache(BagStorage ltstore) throws IOException, CacheManagementException {
        File tf = new File(tempDir.toFile(), "headbags");
        File dbf = new File(tf, "inventory.sqlite");
        // boolean needinit = false;
        if (!tf.exists()) {
            // needinit = true;
            tf.mkdir();
            HeadBagDB.initializeSQLiteDB(dbf.getAbsolutePath());
        }
        HeadBagDB sidb = HeadBagDB.createHeadBagDB(dbf.getAbsolutePath());
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);
        sidb.registerAlgorithm("sha256");
        File cvd = new File(tf, "cv0");
        if (!cvd.exists()) cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv0"), 2000000, null, true);
        cvd = new File(tf, "cv1");
        if (!cvd.exists()) cvd.mkdir();
        cache.addCacheVolume(new FilesystemCacheVolume(cvd, "cv1"), 2000000, null, true);

        return new HeadBagCacheManager(cache, sidb, new HeadBagRestorer(ltstore), "88434");
    }

    ConfigurableCache createDataCache(File croot) throws CacheManagementException, IOException {
        File dbf = new File(croot, "inventory.sqlite");
        PDRStorageInventoryDB.initializeSQLiteDB(dbf.getAbsolutePath());
        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createSQLiteDB(dbf.getPath());
        sidb.registerAlgorithm("sha256");
        ConfigurableCache cache = new ConfigurableCache("headbags", sidb, 2, null);

        File cvdir = new File(croot, "foobar");
        cvdir.mkdir();
        VolumeConfig vc = new VolumeConfig();
        CacheVolume cv = new FilesystemCacheVolume(cvdir, "foobar", "https://goob.net/c/foobar/");
        vc.setRoles(PDRCacheRoles.ROLE_SMALL_OBJECTS | PDRCacheRoles.ROLE_FAST_ACCESS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "cranky");
        cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "cranky", "https://goob.net/c/cranky/");
        vc.setRoles(PDRCacheRoles.ROLE_GENERAL_PURPOSE | PDRCacheRoles.ROLE_LARGE_OBJECTS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        cvdir = new File(croot, "old");
        cvdir.mkdir();
        cv = new FilesystemCacheVolume(cvdir, "old", "https://goob.net/c/old/");
        vc.setRoles(PDRCacheRoles.ROLE_OLD_VERSIONS);
        cache.addCacheVolume(cv, 2000000, null, vc, true);

        return cache;
    }

    private BagStorage createBagStorage(boolean empty) throws IOException {
        return new FilesystemLongTermStorage((empty) ? mtltsdir : ltsdir);
    }

    private void setupFileDownloadService(boolean empty) throws IOException, CacheManagementException {
        BagStorage ltstore = createBagStorage(empty);
        hbcm = createHBCache(ltstore);

        File croot = new File(tempDir.toFile(), "data");
        if (!croot.exists()) {
            croot.mkdir();
            cache = createDataCache(croot);
        }
        rstr = new PDRDatasetRestorer(ltstore, hbcm, 500);

        List<CacheObjectCheck> checks = List.of(new ChecksumCheck());
        mgr = new PDRCacheManager(cache, rstr, checks, 5000, -1, -1, croot, null);

        DefaultPreservationBagService pres = new DefaultPreservationBagService(ltstore);
        FileDownloadService srcsvc = new FromBagFileDownloadService(pres);
        svc = new CacheEnabledFileDownloadService(srcsvc, pres, mgr, hbcm, false);
    }

    @BeforeEach
    public void setUp() throws IOException, CacheManagementException {
        mtltsdir = tempDir.resolve("mtlts").toString();
        setupFileDownloadService(false);
    }

    @Test
    public void testGetDataFileInfo() throws ResourceNotFoundException, DistributionException, IOException {
        FileDescription fd = null;

        // Cache is empty; test fallback functionality (latest version)
        setupFileDownloadService(true);
        assertThrows(ResourceNotFoundException.class, () -> {
            svc.getDataFileInfo("mds1491", "trial3/trial3a.json", "0");
        });

        // Reconnect long-term storage
        setupFileDownloadService(false);
        fd = svc.getDataFileInfo("mds1491", "trial3/trial3a.json", "0");
        assertEquals("trial3/trial3a.json", fd.name);
        assertEquals(70, fd.contentLength);
    }

    @Test
    public void testGetDataFile() throws ResourceNotFoundException, DistributionException, IOException {
        byte[] buf = new byte[200];
        setupFileDownloadService(true);

        assertThrows(ResourceNotFoundException.class, () -> {
            svc.getDataFile("mds1491", "trial3/trial3a.json", "0");
        });

        setupFileDownloadService(false);
        try (StreamHandle sh = svc.getDataFile("mds1491", "trial3/trial3a.json", "0")) {
            assertEquals(70, sh.dataStream.read(buf));
        }
    }

    @Test
    public void testGetDataFileRedirect() throws ResourceNotFoundException, DistributionException, IOException {
        URL redir = null;

        // Start with nothing in the cache; redirect request should return null;
        redir = svc.getDataFileRedirect("mds1491", "trial3/trial3a.json", "0");
        assertNull(redir);
        redir = svc.getDataFileRedirect("mds1491", "trial1.json.sha256", null);
        assertNull(redir);

        // Now cache the data
        mgr.cache("mds1491/trial3/trial3a.json#0");
        mgr.cache("mds1491/trial1.json.sha256");

        redir = svc.getDataFileRedirect("mds1491", "trial3/trial3a.json", "0");
        assertEquals("https://goob.net/c/old/mds1491/trial3/trial3a-v0.json", redir.toString());

        CacheObject co = svc.findCachedObject("mds1491", "trial1.json.sha256", null);
        long accesstime = co.getMetadatumLong("since", -1L);
        assertTrue(accesstime > 0);

        redir = svc.getDataFileRedirect("mds1491", "trial1.json.sha256", null);
        assertEquals("https://goob.net/c/foobar/mds1491/trial1.json.sha256", redir.toString());
        co = svc.findCachedObject("mds1491", "trial1.json.sha256", null);
        assertTrue(accesstime < co.getMetadatumLong("since", -1L));
    }

    @Test
    public void testListDataFiles() throws ResourceNotFoundException, DistributionException {
        List<String> files = null;

        // Look for non-existent resource
        assertThrows(ResourceNotFoundException.class, () -> {
            svc.listDataFiles("goober", null);
        });

        files = svc.listDataFiles("mds1491", "0");
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertEquals(files.size(), 3);

        files = svc.listDataFiles("mds1491", null);
        assertTrue(files.contains("trial1.json"));
        assertTrue(files.contains("trial1.json.sha256"));
        assertTrue(files.contains("trial2.json"));
        assertTrue(files.contains("trial3/trial3a.json"));
        assertTrue(files.contains("trial3/trial3a.json.sha256"));
        assertEquals(files.size(), 5);
    }

    @Test
    public void testEfficientAccess() throws ResourceNotFoundException, DistributionException, IOException {
        URL redir = null;
        CacheObject co = null;
        byte[] buf = new byte[200];

        // Try non-existent file
        co = svc.findCachedObject("goober", "gurn.txt", null);
        assertNull(co);
        assertThrows(ResourceNotFoundException.class, () -> {
            svc.getDataFile("goober", "gurn.txt", null);
        });

        // Try file not in cache
        co = svc.findCachedObject("mds1491", "trial1.json", null);
        assertNull(co);
        try (StreamHandle sh = svc.getDataFile("mds1491", "trial1.json.sha256", null)) {
            assertEquals(90, sh.dataStream.read(buf));
        }

        // Now a file in the cache
        mgr.cache("mds1491/trial3/trial3a.json#0");
        co = svc.findCachedObject("mds1491", "trial3/trial3a.json", "0");
        assertNotNull(co);
        long accesstime = co.getMetadatumLong("since", -1L);
        assertTrue(accesstime > 0);
        redir = svc.redirectFor(co);
        assertEquals("https://goob.net/c/old/mds1491/trial3/trial3a-v0.json", redir.toString());
        try (StreamHandle sh = svc.openStreamFor(co)) {
            assertEquals(70, sh.dataStream.read(buf));
        }
        co = svc.findCachedObject("mds1491", "trial3/trial3a.json", "0");
        assertTrue(accesstime < co.getMetadatumLong("since", -1L));
    }
}
