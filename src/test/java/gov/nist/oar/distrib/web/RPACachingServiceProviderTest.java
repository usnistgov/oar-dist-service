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
        cmcfg.setAdmindir(root.toString());

        rpacfg = (new ObjectMapper()).readValue(getClass().getResourceAsStream("/rpaconfig.json"),
                                                RPAConfiguration.class);
        rpacfg.setBagstoreLocation(new File(tempf, "rpalts").toString());

        Logger logger = LoggerFactory.getLogger("Publts");
        publts = new FilesystemLongTermStorage(new File(tempf, "publts").toString(), 10000000, logger);

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
}
