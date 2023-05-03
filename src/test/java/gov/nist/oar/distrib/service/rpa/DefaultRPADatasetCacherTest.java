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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
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
        version = "";
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
    public void testCache_requestProcessingException() throws Exception {
        // throw a CacheManagementException
        when(rpaCachingService.cacheAndGenerateRandomId(datasetId, version))
                .thenThrow(new CacheManagementException("Error caching dataset: " + datasetId));
        try {
            rpaDatasetCacher.cache(datasetId);
            fail("Expected RequestProcessingException to be thrown");
        } catch (RequestProcessingException e) {
            assertThat(e.getMessage(), containsString("Error caching dataset: " + datasetId));
            verify(rpaCachingService).cacheAndGenerateRandomId(eq(datasetId), anyString());
        }
    }

    @Test
    public void testCache_invalidDatasetId() throws Exception {
        String invalidDatasetId = "ard:/invalid_id/";
        // throw an IllegalArgumentException for the invalid datasetId
        when(rpaCachingService.cacheAndGenerateRandomId(eq(invalidDatasetId), anyString()))
                .thenThrow(new IllegalArgumentException("Invalid dataset ID format: " + invalidDatasetId));

        try {
            rpaDatasetCacher.cache(invalidDatasetId);
            fail("Expected RequestProcessingException to be thrown");
        } catch (RequestProcessingException e) {
            assertThat(e.getMessage(), containsString("Invalid dataset ID format: " + invalidDatasetId));
            verify(rpaCachingService).cacheAndGenerateRandomId(eq(invalidDatasetId), anyString());
        }
    }


}

