package gov.nist.oar.distrib.service.rpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.service.RPACachingService;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;

@ExtendWith(MockitoExtension.class)
public class DefaultRPADatasetCacherTest {

    @Mock
    private RPACachingService rpaCachingService;

    private DefaultRPADatasetCacher rpaDatasetCacher;
    private String datasetId;
    private String version;

    @BeforeEach
    public void setUp() {
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

    @Test
    public void testCache_requestProcessingException() throws Exception {
        when(rpaCachingService.cacheAndGenerateRandomId(datasetId, version))
                .thenThrow(new CacheManagementException("Error caching dataset: " + datasetId));

        assertThrows(RequestProcessingException.class, () -> {
            rpaDatasetCacher.cache(datasetId);
        });
    }

    @Test
    public void testCache_invalidDatasetId() throws Exception {
        String invalidDatasetId = "ark:/invalid_id";

        when(rpaCachingService.cacheAndGenerateRandomId(eq(invalidDatasetId), anyString()))
                .thenThrow(new IllegalArgumentException("Invalid dataset ID format: " + invalidDatasetId));

        assertThrows(RequestProcessingException.class, () -> {
            rpaDatasetCacher.cache(invalidDatasetId);
        });
    }
}
