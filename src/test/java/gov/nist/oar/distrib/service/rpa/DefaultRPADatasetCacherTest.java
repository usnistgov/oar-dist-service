package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultRPADatasetCacherTest {
    @Mock
    private RPACachingService rpaCachingService;
    private DefaultRPADatasetCacher rpaDatasetCacher;
    String datasetId;
    String version;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        rpaDatasetCacher = new DefaultRPADatasetCacher(rpaCachingService);
        datasetId = "mds2-2909";
        version = null;
    }

    @Test
    public void testCache_Success() throws Exception {
        String randomId = "randomId123";
        when(rpaCachingService.cacheAndGenerateRandomId(datasetId, version)).thenReturn(randomId);
        String result = rpaDatasetCacher.cache(datasetId);
        verify(rpaCachingService, times(1)).cacheAndGenerateRandomId(datasetId, version);
        assertEquals(randomId, result);
    }

    @Test
    public void testCache_Success_WithArkID() throws Exception {
        datasetId = "ark:/12345/mds2-2909";
        String randomId = "randomId123";
        when(rpaCachingService.cacheAndGenerateRandomId(datasetId, version)).thenReturn(randomId);
        String result = rpaDatasetCacher.cache(datasetId);
        verify(rpaCachingService, times(1)).cacheAndGenerateRandomId(datasetId, version);
        assertEquals(randomId, result);
    }

    @Test(expected = RequestProcessingException.class)
    public void testCache_requestProcessingException() throws Exception {
        // throw a CacheManagementException
        when(rpaCachingService.cacheAndGenerateRandomId(datasetId, version))
                .thenThrow(new CacheManagementException("Error caching dataset: " + datasetId));
        rpaDatasetCacher.cache(datasetId);
    }

    @Test(expected = RequestProcessingException.class)
    public void testCache_invalidDatasetId() throws Exception {
        String invalidDatasetId = "ark:/invalid_id";

         // Throw an IllegalArgumentException for the invalid datasetId when the cacheAndGenerateRandomId method is called
        when(rpaCachingService.cacheAndGenerateRandomId(eq(invalidDatasetId), any()))
                .thenThrow(new IllegalArgumentException("Invalid dataset ID format: " + invalidDatasetId));

        rpaDatasetCacher.cache(invalidDatasetId);
    }

}

