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
     * An object is considered expired based on the `expiresIn` metadata, which defines the duration after which
     * an object should be considered expired from the time of its last modification. This test ensures that an object
     * past its expiration is appropriately removed from the inventory database.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testExpiredObject() throws Exception {
        // Setup mock
        cacheObject.name = "testObject";
        cacheObject.volname = "testVolume";
        when(cacheObject.hasMetadatum("expiresIn")).thenReturn(true);
        when(cacheObject.getMetadatumLong("expiresIn", -1L)).thenReturn(14 * 24 * 60 * 60 * 1000L); // 14 days in milliseconds
        long lastModified = System.currentTimeMillis() - (15 * 24 * 60 * 60 * 1000L); // 15 days ago
        when(cacheObject.getLastModified()).thenReturn(lastModified);

        // Perform the check
        expiryCheck.check(cacheObject);

        // Verify the interactions
        verify(mockInventoryDB).removeObject(cacheObject.volname, cacheObject.name);
    }


    /**
     * Test to ensure that {@link CacheExpiryCheck} does not flag a cache object as expired if the current time has not
     * exceeded its `expiresIn` duration since its last modification. This test verifies that no removal action is taken
     * for such non-expired objects.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testNonExpiredObject() throws Exception {
        // Setup mock
        cacheObject.name = "nonExpiredObject";
        cacheObject.volname = "testVolume";
        when(cacheObject.hasMetadatum("expiresIn")).thenReturn(true);
        when(cacheObject.getMetadatumLong("expiresIn", -1L)).thenReturn(14 * 24 * 60 * 60 * 1000L); // 14 days in milliseconds
        long lastModified = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days ago, within expiry period
        when(cacheObject.getLastModified()).thenReturn(lastModified);

        // Perform the check
        expiryCheck.check(cacheObject);

        // Verify that the remove method was not called as the object is not expired
        verify(mockInventoryDB, never()).removeObject(cacheObject.volname, cacheObject.name);
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

    /**
     * Test to verify that {@link CacheExpiryCheck} throws an {@link IntegrityException} when a cache object lacks the
     * `expiresIn` metadata. This scenario indicates that the object's expiration cannot be determined, necessitating
     * error handling.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test(expected = IntegrityException.class)
    public void testObjectWithoutExpiresInMetadata() throws Exception {
        // Setup mock to simulate an object without expiresIn metadata
        cacheObject.name = "objectWithoutExpiresIn";
        cacheObject.volname = "testVolume";
        when(cacheObject.hasMetadatum("expiresIn")).thenReturn(false);

        // Attempt to check the object, expecting an IntegrityException
        expiryCheck.check(cacheObject);
    }

}
