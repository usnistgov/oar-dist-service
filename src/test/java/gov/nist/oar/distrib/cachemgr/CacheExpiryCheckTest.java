package gov.nist.oar.distrib.cachemgr;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

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
     * An object is considered expired based on the `expires` metadata, which defines the duration after which
     * an object should be considered expired from the time of its last modification. This test ensures that an object
     * past its expiration is appropriately removed from the inventory database.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testExpiredObjectRemoval() throws Exception {
        // Setup an expired cache object
        cacheObject.volume = mockVolume;
        when(cacheObject.hasMetadatum("expires")).thenReturn(true);
        when(cacheObject.getMetadatumLong("expires", -1L)).thenReturn(1000L); // Expires in 1 second
        when(cacheObject.getLastModified()).thenReturn(Instant.now().minusSeconds(10).toEpochMilli());
        when(cacheObject.volume.remove(cacheObject.name)).thenReturn(true);

        expiryCheck.check(cacheObject);

        // Verify removeObject effect
        verify(mockInventoryDB).removeObject(cacheObject.volname, cacheObject.name);
    }


    /**
     * Test to ensure that {@link CacheExpiryCheck} does not flag a cache object as expired if the current time has not
     * exceeded its `expires` duration since its last modification. This test verifies that no removal action is taken
     * for such non-expired objects.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testNonExpiredObject() throws Exception {
        // Setup mock
        cacheObject.name = "nonExpiredObject";
        cacheObject.volname = "testVolume";
        when(cacheObject.hasMetadatum("expires")).thenReturn(true);
        when(cacheObject.getMetadatumLong("expires", -1L)).thenReturn(14 * 24 * 60 * 60 * 1000L); // 14 days in milliseconds
        long lastModified = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L); // 7 days ago, within expiry period
        when(cacheObject.getLastModified()).thenReturn(lastModified);

        // Perform the check
        expiryCheck.check(cacheObject);

        // Verify that the remove method was not called as the object is not expired
        verify(mockInventoryDB, never()).removeObject(cacheObject.volname, cacheObject.name);
    }

    /**
     * Tests that no action is taken and no exception is thrown for a cache object without the {@code expires} metadata.
     * This verifies that the absence of {@code expires} metadata does not trigger any removal process or result in an error.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test
    public void testObjectWithoutExpiresMetadata_NoActionTaken() throws Exception {
        cacheObject.name = "objectWithoutExpires";
        cacheObject.volname = "testVolume";
        when(cacheObject.hasMetadatum("expires")).thenReturn(false);

        expiryCheck.check(cacheObject);

        verify(mockInventoryDB, never()).removeObject(anyString(), anyString());
    }

    /**
     * Test to ensure that a cache object with an expiration date in the future is not removed from the cache.
     * This test verifies the {@code check} method's correct behavior in handling non-expired objects based
     * on the {@code expires} metadata.
     *
     * @throws Exception to handle any exceptions thrown during the test execution.
     */
    @Test
    public void testNonExpiredObject_NoRemoval() throws Exception {
        // Setup a non-expired cache object
        when(cacheObject.hasMetadatum("expires")).thenReturn(true);
        when(cacheObject.getMetadatumLong("expires", -1L)).thenReturn(System.currentTimeMillis() + 10000L); // Expires in the future
        when(cacheObject.getLastModified()).thenReturn(System.currentTimeMillis());

        expiryCheck.check(cacheObject);

        // Verify no removal happens
        verify(mockInventoryDB, never()).removeObject(anyString(), anyString());
    }


    /**
     * Tests that an {@link IntegrityException} is thrown when a cache object has the {@code expires} metadata
     * but lacks a valid {@code lastModified} time.
     *
     * @throws Exception to handle any exceptions thrown during the test execution
     */
    @Test(expected = IntegrityException.class)
    public void testObjectWithExpiresButNoLastModified_ThrowsException() throws Exception {
        cacheObject.name = "objectWithNoLastModified";
        cacheObject.volname = "testVolume";
        when(cacheObject.hasMetadatum("expires")).thenReturn(true);
        when(cacheObject.getMetadatumLong("expires", -1L)).thenReturn(1000L); // Expires in 1 second
        when(cacheObject.getLastModified()).thenReturn(-1L); // Last modified not available

        expiryCheck.check(cacheObject);
    }

    /**
     * Test to verify that no action is taken for a cache object missing the {@code expires} metadata.
     * This test ensures that the absence of {@code expires} metadata does not trigger any removal or error.
     *
     * @throws Exception to handle any exceptions thrown during the test execution.
     */
    @Test
    public void testObjectWithoutExpires_NoAction() throws Exception {
        // Setup an object without expires metadata
        when(cacheObject.hasMetadatum("expires")).thenReturn(false);

        expiryCheck.check(cacheObject);

        // Verify no action is taken
        verify(mockInventoryDB, never()).removeObject(anyString(), anyString());
    }

}
