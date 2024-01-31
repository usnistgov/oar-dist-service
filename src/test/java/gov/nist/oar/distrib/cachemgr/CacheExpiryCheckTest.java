package gov.nist.oar.distrib.cachemgr;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CacheExpiryCheckTest {

    @Mock
    private StorageInventoryDB mockInventoryDB;
    @Mock
    private CacheVolume mockVolume;
    @Mock
    private CacheObject cacheObject;
    private CacheExpiryCheck expiryCheck;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        expiryCheck = new CacheExpiryCheck(mockInventoryDB);
    }

    /**
     * Test to verify that {@link CacheExpiryCheck} correctly identifies and processes an expired cache object.
     * An object is considered expired if its last modified time is more than two weeks ago.
     * This test checks that the expired object is appropriately removed from both the cache volume and the inventory
     * database.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testExpiredObject() throws Exception {
        // Setup mock
        cacheObject.name = "testObject";
        cacheObject.volname = "testVolume";
        cacheObject.volume = mockVolume;
        long lastModified = System.currentTimeMillis() - (15 * 24 * 60 * 60 * 1000); // 15 days in milliseconds
        when(cacheObject.getLastModified()).thenReturn(lastModified);


        when(mockVolume.remove("testObject")).thenReturn(true);

        // Perform the check
        expiryCheck.check(cacheObject);

        // Verify the interactions
        verify(mockVolume).remove("testObject");
        verify(mockInventoryDB).removeObject(anyString(), anyString());
    }


    /**
     * Test to ensure that {@link CacheExpiryCheck} does not flag a cache object as expired if it has been
     * modified within the last two weeks. This test checks that no removal action is taken for a non-expired object.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testNonExpiredObject() throws Exception {
        // Setup mock
        cacheObject.name = "nonExpiredObject";
        cacheObject.volname = "testVolume";
        cacheObject.volume = mockVolume;

        long lastModified = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000); // 5 days in milliseconds
        when(cacheObject.getLastModified()).thenReturn(lastModified);

        // Perform the check
        expiryCheck.check(cacheObject);

        // Verify that the remove method was not called as the object is not expired
        verify(mockVolume, never()).remove("nonExpiredObject");
        verify(mockInventoryDB, never()).removeObject("testVolume", "nonExpiredObject");
    }

    /**
     * Test to verify that {@link CacheExpiryCheck} throws an {@link IntegrityException} when the last modified time
     * of a cache object is unknown (indicated by a value of -1). This situation should be flagged as an error
     * as the expiry status of the object cannot be determined.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test(expected = IntegrityException.class)
    public void testUnknownLastModifiedTime() throws Exception {
        // Setup mock
        cacheObject.name = "unknownLastModifiedObject";
        cacheObject.volname = "testVolume";
        cacheObject.volume = mockVolume;
        long lastModified = -1; // Unknown last modified time
        when(cacheObject.getLastModified()).thenReturn(lastModified);

        // Perform the check, expecting an IntegrityException
        expiryCheck.check(cacheObject);
    }

}
