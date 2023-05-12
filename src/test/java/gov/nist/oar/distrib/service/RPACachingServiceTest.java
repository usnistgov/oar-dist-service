package gov.nist.oar.distrib.service;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.pdr.PDRCacheManager;
import gov.nist.oar.distrib.service.rpa.exceptions.MetadataNotFoundException;
import gov.nist.oar.distrib.service.rpa.exceptions.RequestProcessingException;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private RPAConfiguration rpaConfiguration;

    @Before
    public void setUp() {
        pdrCacheManager = mock(PDRCacheManager.class);
        rpaConfiguration = mock(RPAConfiguration.class);
        rpaCachingService = new RPACachingService(pdrCacheManager, rpaConfiguration);
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

    @Test
    public void testRetrieveMetadata_success() throws Exception  {
        String randomID = "randomId123";
        CacheObject cacheObject1 = new CacheObject("object1", new JSONObject()
                .put("filepath", "path/to/file1.txt")
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 1")
                .put("pdrid", "123456")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "abc123")
                .put("version", "v1")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");

        CacheObject cacheObject2 = new CacheObject("object2", new JSONObject()
                .put("filepath", "path/to/file2.txt")
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 2")
                .put("pdrid", "654321")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "def456")
                .put("version", "v2")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");

        List<CacheObject> cacheObjects = Arrays.asList(cacheObject1, cacheObject2);

        when(pdrCacheManager.selectDatasetObjects(randomID, pdrCacheManager.VOL_FOR_INFO))
                .thenReturn(cacheObjects);

        String testBaseDownloadUrl = "https://testdata.nist.gov";
        when(rpaConfiguration.getBaseDownloadUrl()).thenReturn(testBaseDownloadUrl);

        Map<String, Object> expected = new HashMap<>();
        expected.put("randomId", randomID);
        expected.put("metadata", new JSONArray()
                .put(new JSONObject().put("downloadURL", testBaseDownloadUrl + "/" + randomID + "/path/to/file1.txt")
                        .put("filePath", "path/to/file1.txt")
                        .put("mediaType", "text/plain")
                        .put("size", 100L)
                        .put("resTitle", "Resource 1")
                        .put("resId", "123456")
                        .put("checksumAlgorithm", "SHA256")
                        .put("checksum", "abc123")
                        .put("version", "v1")
                        .put("ediid", "123")
                        .put("aipid", "456")
                        .put("sinceDate", "08-05-2023"))
                .put(new JSONObject().put("downloadURL", testBaseDownloadUrl + "/" + randomID + "/path/to/file2.txt")
                        .put("filePath", "path/to/file2.txt")
                        .put("mediaType", "text/plain")
                        .put("size", 100L)
                        .put("resTitle", "Resource 2")
                        .put("resId", "654321")
                        .put("checksumAlgorithm", "SHA256")
                        .put("checksum", "def456")
                        .put("version", "v2")
                        .put("ediid", "123")
                        .put("aipid", "456")
                        .put("sinceDate", "08-05-2023"))
                .toList());

        Map<String, Object> actual = rpaCachingService.retrieveMetadata(randomID);

        assertEquals(expected, actual);
    }

    // This tests if objects with missing filepaths are skipped and not included in metadata
    @Test
    public void testRetrieveMetadata_withMissingFilepath() throws Exception  {
        String randomID = "randomId123";
        CacheObject cacheObject1 = new CacheObject("object1", new JSONObject()
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 1")
                .put("pdrid", "123456")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "abc123")
                .put("version", "v1")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");

        CacheObject cacheObject2 = new CacheObject("object2", new JSONObject()
                .put("filepath", "path/to/file2.txt")
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 2")
                .put("pdrid", "654321")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "def456")
                .put("version", "v2")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");

        List<CacheObject> cacheObjects = Arrays.asList(cacheObject1, cacheObject2);

        when(pdrCacheManager.selectDatasetObjects(randomID, pdrCacheManager.VOL_FOR_INFO))
                .thenReturn(cacheObjects);

        String testBaseDownloadUrl = "https://testdata.nist.gov";
        when(rpaConfiguration.getBaseDownloadUrl()).thenReturn(testBaseDownloadUrl);

        Map<String, Object> expected = new HashMap<>();
        expected.put("randomId", randomID);
        expected.put("metadata", new JSONArray()
                .put(new JSONObject().put("downloadURL", testBaseDownloadUrl + "/" + randomID + "/path/to/file2.txt")
                        .put("filePath", "path/to/file2.txt")
                        .put("mediaType", "text/plain")
                        .put("size", 100L)
                        .put("resTitle", "Resource 2")
                        .put("resId", "654321")
                        .put("checksumAlgorithm", "SHA256")
                        .put("checksum", "def456")
                        .put("version", "v2")
                        .put("ediid", "123")
                        .put("aipid", "456")
                        .put("sinceDate", "08-05-2023"))
                .toList());

        Map<String, Object> actual = rpaCachingService.retrieveMetadata(randomID);

        assertEquals(expected, actual);
    }

    // This should throw MetadataNotFoundException since there is only one object but it's missing filepath
    @Test(expected = MetadataNotFoundException.class)
    public void testRetrieveMetadata_withOneObjectMissingFilepath() throws Exception  {
        String randomID = "randomId123";
        CacheObject cacheObject1 = new CacheObject("object1", new JSONObject()
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 1")
                .put("pdrid", "123456")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "abc123")
                .put("version", "v1")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");


        List<CacheObject> cacheObjects = Arrays.asList(cacheObject1);

        when(pdrCacheManager.selectDatasetObjects(randomID, pdrCacheManager.VOL_FOR_INFO))
                .thenReturn(cacheObjects);

        rpaCachingService.retrieveMetadata(randomID);
    }

    @Test(expected = MetadataNotFoundException.class)
    public void testRetrieveMetadata_WithEmptyMetadataList() throws Exception  {

        String randomID = "randomId123";
        List<CacheObject> objects = new ArrayList<>();
        when(pdrCacheManager.selectDatasetObjects(randomID, pdrCacheManager.VOL_FOR_INFO)).thenReturn(objects);

        rpaCachingService.retrieveMetadata(randomID);
    }

    @Test(expected = RequestProcessingException.class)
    public void testRetrieveMetadata_WithMalformedBaseUrl() throws Exception  {


        String randomID = "randomId123";
        CacheObject cacheObject1 = new CacheObject("object1", new JSONObject()
                .put("filepath", "path/to/file1.txt")
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 1")
                .put("pdrid", "123456")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "abc123")
                .put("version", "v1")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");

        CacheObject cacheObject2 = new CacheObject("object2", new JSONObject()
                .put("filepath", "path/to/file2.txt")
                .put("contentType", "text/plain")
                .put("size", 100L)
                .put("title", "Resource 2")
                .put("pdrid", "654321")
                .put("checksumAlgorithm", "SHA256")
                .put("checksum", "def456")
                .put("version", "v2")
                .put("ediid", "123")
                .put("aipid", "456")
                .put("sinceDate", "08-05-2023"),
                "Volume1");

        List<CacheObject> cacheObjects = Arrays.asList(cacheObject1, cacheObject2);

        when(pdrCacheManager.selectDatasetObjects(randomID, pdrCacheManager.VOL_FOR_INFO))
                .thenReturn(cacheObjects);

        String testBaseDownloadUrl = "htp://testdata.nist.gov/";
        when(rpaConfiguration.getBaseDownloadUrl()).thenReturn(testBaseDownloadUrl);

        rpaCachingService.retrieveMetadata(randomID);
    }

}

