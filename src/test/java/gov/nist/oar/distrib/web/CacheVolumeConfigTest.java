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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.DeletionStrategy;
import gov.nist.oar.distrib.cachemgr.VolumeStatus;
import gov.nist.oar.distrib.cachemgr.inventory.BigOldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.BySizeSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;

public class CacheVolumeConfigTest {

    @TempDir
    File tempf;

    NISTCacheManagerConfig cmcfg = new NISTCacheManagerConfig();
    NISTCacheManagerConfig.CacheVolumeConfig cfg = null;
    File voldir = null;

    @BeforeEach
    public void setUp() throws IOException {
        cmcfg.setAdmindir(tempf.getAbsolutePath());
        voldir = new File(cmcfg.getAdmindir(), "volume");
        voldir.mkdir();
        
        cfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        cfg.setCapacity(2000L);
        cfg.setLocation("file://" + voldir.toString());
        cfg.setStatus("update");
        List<String> roles = new ArrayList<>();
        roles.add("general");
        roles.add("large");
        cfg.setRoles(roles);
        cfg.setRedirectBase("http://data.nist.gov/cache/goob");
        cfg.setName("goob");
        Map<String, Object> strat = new HashMap<>();
        strat.put("type", "oldest");
        strat.put("priority0", 20);
        cfg.setDeletionStrategy(strat);
    }

    @Test
    public void testGetStatusCode() throws ConfigurationException {
        assertEquals(VolumeStatus.VOL_FOR_UPDATE, cfg.getStatusCode());

        cfg.setStatus("get");
        assertEquals(VolumeStatus.VOL_FOR_GET, cfg.getStatusCode());

        cfg.setStatus("info");
        assertEquals(VolumeStatus.VOL_FOR_INFO, cfg.getStatusCode());

        cfg.setStatus("goober");
        assertThrows(ConfigurationException.class, () -> cfg.getStatusCode());
    }

    @Test
    public void testCreateDeletionStrategy() throws ConfigurationException {
        DeletionStrategy ds = cfg.createDeletionStrategy();
        assertTrue(ds instanceof OldSelectionStrategy);
        assertEquals(20, ((OldSelectionStrategy) ds).getNormalPriority());

        Map<String, Object> strat = new HashMap<>();
        strat.put("type", "biggest");
        strat.put("priority0", 20);  // should be ignored
        strat.put("normSize", 50);
        cfg.setDeletionStrategy(strat);
        ds = cfg.createDeletionStrategy();
        assertTrue(ds instanceof BySizeSelectionStrategy);
        assertEquals(50.0, ((BySizeSelectionStrategy) ds).getNormalizingSize(), 0.01);

        strat.put("normSize", 50.5);
        cfg.setDeletionStrategy(strat);
        ds = cfg.createDeletionStrategy();
        assertTrue(ds instanceof BySizeSelectionStrategy);
        assertEquals(50.5, ((BySizeSelectionStrategy) ds).getNormalizingSize(), 0.01);

        strat.put("type", "bigoldest");
        cfg.setDeletionStrategy(strat);
        ds = cfg.createDeletionStrategy();
        assertTrue(ds instanceof BigOldSelectionStrategy);
        assertEquals(9000000.0, ((BigOldSelectionStrategy) ds).getTurnOverAge(), 0.01);
        assertEquals(5.0e8, ((BigOldSelectionStrategy) ds).getTurnOverSize(), 0.01e8);

        strat.put("ageTurnOver", 200000.0);
        strat.put("sizeTurnOver", 40000);
        ds = cfg.createDeletionStrategy();
        assertTrue(ds instanceof BigOldSelectionStrategy);
        assertEquals(200000.0, ((BigOldSelectionStrategy) ds).getTurnOverAge(), 0.01);
        assertEquals(4.0e4, ((BigOldSelectionStrategy) ds).getTurnOverSize(), 0.01e4);

        strat.put("type", "dumbest");
        assertThrows(ConfigurationException.class, () -> cfg.createDeletionStrategy());
    }

    @Test
    public void testCreateCacheVolume() throws ConfigurationException, FileNotFoundException, MalformedURLException, CacheManagementException {
        CacheVolume cv = cfg.createCacheVolume(cmcfg, null);
        assertTrue(cv instanceof FilesystemCacheVolume);
        assertEquals(voldir, ((FilesystemCacheVolume) cv).getRootDir());

        cfg.setLocation("s3://nist-goober/gurn");
        assertThrows(ConfigurationException.class, () -> cfg.createCacheVolume(cmcfg, null));

        cfg.setLocation("next://nist-goober/gurn");
        assertThrows(ConfigurationException.class, () -> cfg.createCacheVolume(cmcfg, null));

        cfg.setLocation("/oar/data/nist-goober/gurn");
        assertThrows(ConfigurationException.class, () -> cfg.createCacheVolume(cmcfg, null));
    }
}
