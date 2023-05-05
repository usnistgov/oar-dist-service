package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RPACachingServiceTest {

    private RPACachingService rpaCachingService;
    private PDRCacheManager pdrCacheManager;

    @Before
    public void setUp() {
        pdrCacheManager = mock(PDRCacheManager.class);
        rpaCachingService = new RPACachingService(pdrCacheManager);
    }

    @Test
    public void testCacheAndGenerateRandomId_validDatasetID() throws Exception {
        String datasetID = "mds2-2909";

        String version = "";
        Set<String> dummyFiles = new HashSet<>();
        when(pdrCacheManager.cacheDataset(anyString(), eq(version), eq(true), eq(RPACachingService.ROLE_RESTRICTED_DATA), anyString()))
                .thenReturn(dummyFiles);

        String result = rpaCachingService.cacheAndGenerateRandomId(datasetID, version);

        assertNotNull(result);
        assertEquals(RPACachingService.RANDOM_ID_LENGTH, result.length());
        assertTrue(result.matches("^[a-zA-Z0-9]*$")); // check that the ID is alphanumeric
        verify(pdrCacheManager).cacheDataset(eq("mds2-2909"), eq(version), eq(true), eq(RPACachingService.ROLE_RESTRICTED_DATA), eq(result));
    }

    @Test
    public void testCacheAndGenerateRandomId_validDatasetArkID() throws Exception {
        String datasetID = "ark:/12345/mds2-2909";

        String version = "";
        Set<String> dummyFiles = new HashSet<>();
        when(pdrCacheManager.cacheDataset(anyString(), eq(version), eq(true), eq(RPACachingService.ROLE_RESTRICTED_DATA), anyString()))
                .thenReturn(dummyFiles);

        String result = rpaCachingService.cacheAndGenerateRandomId(datasetID, version);

        assertNotNull(result);
        assertEquals(RPACachingService.RANDOM_ID_LENGTH, result.length());
        assertTrue(result.matches("^[a-zA-Z0-9]*$")); // check that the ID is alphanumeric
        verify(pdrCacheManager).cacheDataset(eq("mds2-2909"), eq(version), eq(true), eq(RPACachingService.ROLE_RESTRICTED_DATA), eq(result));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCacheAndGenerateRandomId_invalidDatasetArkID() throws Exception {
        String datasetID = "ark:/invalid_ark_id";
        String version = "";

        rpaCachingService.cacheAndGenerateRandomId(datasetID, version);
    }
}

