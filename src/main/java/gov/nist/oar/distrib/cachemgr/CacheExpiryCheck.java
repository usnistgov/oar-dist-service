package gov.nist.oar.distrib.cachemgr;

import gov.nist.oar.distrib.StorageVolumeException;

import java.time.Instant;

/**
 * Implements a cache object check to identify and remove objects that have been in the cache
 * longer than a specified duration, specifically two weeks. This check helps in
 * managing cache integrity by ensuring that stale or outdated data are removed
 * from the cache.
 */
public class CacheExpiryCheck implements CacheObjectCheck {

    // private static final long TWO_WEEKS_MILLIS = 14 * 24 * 60 * 60 * 1000; // 14 days in milliseconds
    private StorageInventoryDB inventoryDB;

    public CacheExpiryCheck(StorageInventoryDB inventoryDB) {
        this.inventoryDB = inventoryDB;
    }

    /**
     * Checks if a cache object is expired and removes it from the cache if it is.
     * The method uses the {@code expiresIn} metadata field to determine the expiration status.
     * The expiration time is calculated based on the {@code LastModified} time plus the {@code expiresIn} duration.
     * If the current time is past the calculated expiry time, the object is removed from the inventory database.
     *
     * @param co The cache object to check for expiration.
     * @throws IntegrityException If the object is found to be corrupted during the check.
     * @throws StorageVolumeException If there's an error accessing the storage volume during the check.
     * @throws CacheManagementException If there's an error managing the cache, including removing the expired object.
     */
    @Override
    public void check(CacheObject co) throws IntegrityException, StorageVolumeException, CacheManagementException {
        if (co == null || inventoryDB == null) {
            throw new IllegalArgumentException("CacheObject or StorageInventoryDB is null");
        }

        if (co.hasMetadatum("expiresIn")) {
            long expiresInDuration = co.getMetadatumLong("expiresIn", -1L);
            if (expiresInDuration == -1L) {
                throw new IntegrityException("Invalid 'expiresIn' metadata value");
            }

            long lastModified = co.getLastModified();
            if (lastModified == -1L) {
                throw new IntegrityException("CacheObject 'lastModified' time not available");
            }

            long expiryTime = lastModified + expiresInDuration;
            long currentTime = Instant.now().toEpochMilli();

            // Check if the object is expired
            if (expiryTime < currentTime) {
                try {
                    boolean removed = removeObject(co);
                    if (!removed) {
                        throw new CacheManagementException("Failed to remove expired object: " + co.name);
                    }
                } catch (InventoryException e) {
                    throw new CacheManagementException("Error removing expired object from inventory database: " + co.name, e);
                }
            }
        }
    }

    /**
     * Attempts to remove a cache object from both its physical volume and the inventory database.
     * Synchronization ensures thread-safe removal operations.
     *
     * @param co The cache object to be removed.
     * @return true if the object was successfully removed from its volume, false otherwise.
     * @throws StorageVolumeException if an error occurs accessing the storage volume.
     * @throws InventoryException if an error occurs updating the inventory database.
     */
    protected boolean removeObject(CacheObject co) throws StorageVolumeException, InventoryException {
        synchronized (inventoryDB) {
            boolean out = co.volume.remove(co.name);
            inventoryDB.removeObject(co.volname, co.name);
            return out;
        }
    }
}
