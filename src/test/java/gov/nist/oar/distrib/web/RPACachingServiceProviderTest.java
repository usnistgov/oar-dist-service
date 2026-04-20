package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.cachemgr.pdr.RestrictedDatasetRestorer;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;

import java.io.IOException;
import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class RPACachingServiceProviderTest {

    @TempDir
    File tempf;

    File root = null;
    NISTCacheManagerConfig cmcfg = null;
    RPAConfiguration rpacfg = null;
    BagStorage publts = null;
    RPACachingServiceProvider prov = null;

    @BeforeEach
    public void setUp() throws IOException {
        cmcfg = new NISTCacheManagerConfig();
        root = new File(tempf, "rpaadm");
        if (!root.exists()) {
            root.mkdirs();  // Ensure the directory is created
        }
        cmcfg.setAdmindir(root.toString());

        File rpaltsDir = new File(tempf, "rpalts");
        if (!rpaltsDir.exists()) {
            rpaltsDir.mkdirs();  
        }
        rpacfg = (new ObjectMapper()).readValue(getClass().getResourceAsStream("/rpaconfig.json"), 
                                                RPAConfiguration.class);
        rpacfg.setBagstoreLocation(rpaltsDir.toString());

        // Ensure publts directory is created
        File publtsDir = new File(tempf, "publts");
        if (!publtsDir.exists()) {
            publtsDir.mkdirs();  
        }

        Logger logger = LoggerFactory.getLogger("publts");
        publts = new FilesystemLongTermStorage(publtsDir.toString(), 10000000, logger);

        prov = new RPACachingServiceProvider(cmcfg, rpacfg, publts, null);
    }

    @AfterEach
    public void tearDown() throws IOException {
        tempf.delete();
        cmcfg = null;
        rpacfg = null;
        publts = null;
        prov = null;
    }

    @Test
    public void testGetRPBagStorage() throws ConfigurationException {
        BagStorage bs = prov.getRPBagStorage();
        assertTrue(bs instanceof FilesystemLongTermStorage);
        assertSame(bs, prov.getRPBagStorage());
    }

    @Test
    public void testGetHeadBagManager()
        throws ConfigurationException, IOException, CacheManagementException
    {
        HeadBagCacheManager hbcm = prov.getHeadBagCacheManager();
        assertEquals("88434", hbcm.getARKNAAN());
        assertSame(hbcm, prov.getHeadBagCacheManager());

        File artif = new File(root, "rpaHeadbags/inventory.sqlite");
        assertTrue(artif.isFile());
        artif = new File(root, "rpaHeadbags/cv0");
        assertTrue(artif.isDirectory());
    }

    @Test
    public void testCreateRPDatasetRestorer()
        throws ConfigurationException, IOException, CacheManagementException
    {
        RestrictedDatasetRestorer rest = prov.createRPDatasetRestorer();
        assertEquals(1209600000L, rest.getExpiryTime());
    }

    @Test
    public void testDefaultDatabaseConfigForRPA() {
        // Verify RPA uses default configuration (no JDBC URL means SQLite)
        assertNull(cmcfg.getDburl());
        assertNull(cmcfg.getRpaDburl());
    }

    @Test
    public void testRPAPostgresConfigMissingUrl() {
        // Test that RPA headbag creation with incomplete PostgreSQL JDBC URL throws exception
        cmcfg.setRpaDburl("jdbc:postgresql:");
        // Incomplete PostgreSQL URL

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> {
            prov.getHeadBagCacheManager();
        });

        assertTrue(ex.getMessage().contains("PostgreSQL database URL"));
    }

    @Test
    public void testRPAPostgresConfigEmptyUrl() {
        // Test that empty PostgreSQL URL is treated as missing for RPA headbag cache
        cmcfg.setRpaDburl("jdbc:postgresql:");

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> {
            prov.getHeadBagCacheManager();
        });

        assertTrue(ex.getMessage().contains("PostgreSQL database URL"));
    }

    @Test
    public void testRPASqliteWithJdbcUrl()
        throws ConfigurationException, IOException, CacheManagementException
    {
        // Test that RPA SQLite works with jdbc:sqlite: URL format
        File rpahbdir = new File(root, "rpaHeadbags");
        File sqliteDb = new File(rpahbdir, "test.sqlite");
        cmcfg.setRpaDburl("jdbc:sqlite:" + sqliteDb.getAbsolutePath());

        // Should create SQLite database successfully
        HeadBagCacheManager hbcm = prov.getHeadBagCacheManager();
        assertNotNull(hbcm);

        // Verify SQLite file was created at the specified location
        assertTrue(sqliteDb.isFile());
    }
}
