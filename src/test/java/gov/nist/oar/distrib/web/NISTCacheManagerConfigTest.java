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
package gov.nist.oar.distrib.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.inventory.SQLiteStorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheRoles;
import gov.nist.oar.distrib.cachemgr.pdr.PDRDatasetRestorer;
import gov.nist.oar.distrib.cachemgr.BasicCache;

import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class NISTCacheManagerConfigTest {

    @TempDir
    Path tempf;

    NISTCacheManagerConfig cfg = null;
    File rootdir = null;

    @BeforeEach
    public void setUp() throws IOException {
        rootdir = Files.createDirectory(tempf.resolve("cache")).toFile();
        cfg = new NISTCacheManagerConfig();
        cfg.setAdmindir(rootdir.toString());

        List<NISTCacheManagerConfig.CacheVolumeConfig> vols = 
            new ArrayList<NISTCacheManagerConfig.CacheVolumeConfig>();
        
        NISTCacheManagerConfig.CacheVolumeConfig vcfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        vcfg.setCapacity(2000L);
        File vdir = new File(rootdir, "vols/king");
        vdir.mkdirs();
        vcfg.setLocation("file://" + vdir.toString());
        vcfg.setStatus("update");
        List<String> roles = new ArrayList<>();
        roles.add("general");
        roles.add("large");
        vcfg.setRoles(roles);
        vcfg.setRedirectBase("http://data.nist.gov/cache/king");
        vcfg.setName("king");
        Map<String, Object> strat = new HashMap<>();
        strat.put("type", "oldest");
        strat.put("priority0", 20);
        vcfg.setDeletionStrategy(strat);
        vols.add(vcfg);

        vcfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        vcfg.setCapacity(3000L);
        vdir = new File(rootdir, "vols/pratt");
        vdir.mkdirs();
        vcfg.setLocation("file://" + vdir.toString());
        vcfg.setStatus("update");
        roles = new ArrayList<>();
        roles.add("old");
        roles.add("small");
        vcfg.setRoles(roles);
        vcfg.setRedirectBase("http://data.nist.gov/cache/pratt");
        vcfg.setName("pratt");
        strat = new HashMap<>();
        strat.put("type", "bigoldest");
        vcfg.setDeletionStrategy(strat);
        vols.add(vcfg);

        vcfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        vcfg.setCapacity(5000L);
        vdir = new File(rootdir, "vols/topsecret");
        vdir.mkdirs();
        vcfg.setLocation("file://" + vdir.toString());
        vcfg.setStatus("update");
        roles = new ArrayList<>();
        roles.add("restricted");
        vcfg.setRoles(roles);
        vcfg.setRedirectBase("http://data.nist.gov/cache/topsecret");
        vcfg.setName("topsecret");
        strat = new HashMap<>();
        strat.put("type", "bigoldest");
        vcfg.setDeletionStrategy(strat);
        vols.add(vcfg);

        vcfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        vcfg.setCapacity(5000L);
        vdir = new File(rootdir, "vols/oldtopsecret");
        vdir.mkdirs();
        vcfg.setLocation("file://" + vdir.toString());
        vcfg.setStatus("update");
        roles = new ArrayList<>();
        roles.add("old-restricted");
        vcfg.setRoles(roles);
        vcfg.setRedirectBase("http://data.nist.gov/cache/oldtopsecret");
        vcfg.setName("oldtopsecret");
        strat = new HashMap<>();
        strat.put("type", "bigoldest");
        vcfg.setDeletionStrategy(strat);
        vols.add(vcfg);

        cfg.setVolumes(vols);
    }

    BagStorage makeBagStorage() throws IOException {
        return new FilesystemLongTermStorage(Files.createDirectory(tempf.resolve("lts")).toString());
    }

    @Test
    public void testCreateHeadBagManager() throws ConfigurationException, IOException, CacheManagementException {
        BagStorage bags = makeBagStorage();
        HeadBagCacheManager hbcmgr = cfg.createHeadBagManager(bags);
        assertEquals("88434", hbcmgr.getARKNAAN());
        File root = Files.createDirectories(tempf.resolve("cache/headbags")).toFile();
        assertTrue(root.isDirectory());
        File inv = new File(root, "inventory.sqlite");
        assertTrue(inv.isFile());
        assertTrue(new File(root, "cv0").isDirectory());
        assertTrue(new File(root, "cv1").isDirectory());

        SQLiteStorageInventoryDB db = new SQLiteStorageInventoryDB(inv.toString());
        JSONObject cvi = db.getVolumeInfo("cv0");
        assertEquals(25000000L, cvi.getLong("capacity"));
        cvi = db.getVolumeInfo("cv1");
        assertEquals(25000000L, cvi.getLong("capacity"));
    }

    @Test
    public void testCreateHeadBagManager2() throws ConfigurationException, IOException, CacheManagementException {
        cfg.setHeadbagCacheSize(2000000L);
        cfg.setArkNaan("88888");

        HeadBagCacheManager hbcmgr = cfg.createHeadBagManager(makeBagStorage());
        assertEquals("88888", hbcmgr.getARKNAAN());
        File root = Files.createDirectories(tempf.resolve("cache/headbags")).toFile();
        assertTrue(root.isDirectory());
        File inv = new File(root, "inventory.sqlite");
        assertTrue(inv.isFile());
        assertTrue(new File(root, "cv0").isDirectory());
        assertTrue(new File(root, "cv1").isDirectory());

        SQLiteStorageInventoryDB db = new SQLiteStorageInventoryDB(inv.toString());
        JSONObject cvi = db.getVolumeInfo("cv0");
        assertEquals(1000000L, cvi.getLong("capacity"));
        cvi = db.getVolumeInfo("cv1");
        assertEquals(1000000L, cvi.getLong("capacity"));
    }

    @Test
    public void testCreateDefaultRestorer() throws ConfigurationException, IOException, CacheManagementException {
        BagStorage bags = makeBagStorage();
        HeadBagCacheManager hbcmgr = cfg.createHeadBagManager(bags);

        PDRDatasetRestorer restr = cfg.createDefaultRestorer(bags, hbcmgr);
        assertEquals(10000000L, restr.getSmallSizeLimit());

        cfg.setSmallSizeLimit(200000000);
        restr = cfg.createDefaultRestorer(bags, hbcmgr);
        assertEquals(200000000L, restr.getSmallSizeLimit());
    }

    @Test
    public void testCreateDefaultCache() throws ConfigurationException, IOException, CacheManagementException {
        cfg.createDefaultCache(null);
        File inv = new File(rootdir, "data.sqlite");
        assertTrue(rootdir.isDirectory());
        assertEquals(rootdir.toString() +"/data.sqlite" , inv.toString());
        assertTrue(inv.isFile());
        assertTrue(new File(rootdir, "vols/king").isDirectory());
        assertTrue(new File(rootdir, "vols/pratt").isDirectory());

        SQLiteStorageInventoryDB db = new SQLiteStorageInventoryDB(inv.toString());
        JSONObject cvi = db.getVolumeInfo("king");
        assertEquals(2000L, cvi.getLong("capacity"));
        assertEquals(PDRCacheRoles.ROLE_GENERAL_PURPOSE | PDRCacheRoles.ROLE_LARGE_OBJECTS, cvi.getInt("roles"));

        cvi = db.getVolumeInfo("pratt");
        assertEquals(3000L, cvi.getLong("capacity"));
        assertEquals(PDRCacheRoles.ROLE_OLD_VERSIONS | PDRCacheRoles.ROLE_SMALL_OBJECTS, cvi.getInt("roles"));

        cvi = db.getVolumeInfo("topsecret");
        assertEquals(5000L, cvi.getLong("capacity"));
        assertEquals(PDRCacheRoles.ROLE_RESTRICTED_DATA, cvi.getInt("roles"));

        cvi = db.getVolumeInfo("oldtopsecret");
        assertEquals(5000L, cvi.getLong("capacity"));
        assertEquals(PDRCacheRoles.ROLE_OLD_RESTRICTED_DATA, cvi.getInt("roles"));
    }

    @Test
    public void testCreateCacheManager() throws ConfigurationException, IOException, CacheManagementException {
        BagStorage bags = makeBagStorage();
        HeadBagCacheManager hbcmgr = cfg.createHeadBagManager(bags);
        PDRDatasetRestorer restr = cfg.createDefaultRestorer(bags, hbcmgr);
        BasicCache cache = cfg.createDefaultCache(null);

        PDRCacheManager cmgr = cfg.createCacheManager(cache, restr, LoggerFactory.getLogger("cachemgr"));
        assertFalse(cmgr.getMonitorThread().isAlive());
        assertNull(cmgr.findObject("goober"));

        JSONObject vsum = cmgr.summarizeVolume("king");
        assertEquals(2000L, vsum.getLong("capacity"));
        assertEquals(0, vsum.getLong("totalsize"));
        assertEquals(0, vsum.getInt("filecount"));
    }
}
