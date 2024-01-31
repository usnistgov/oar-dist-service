package gov.nist.oar.distrib.cachemgr;

import gov.nist.oar.distrib.StorageVolumeException;

/**
 * Implements a cache object check to identify and remove objects that have been in the cache
 * longer than a specified duration, specifically two weeks. This check helps in
 * managing cache integrity by ensuring that stale or outdated data are removed
 * from the cache.
 */
public class CacheExpiryCheck implements CacheObjectCheck {

    private static final long TWO_WEEKS_MILLIS = 14 * 24 * 60 * 60 * 1000; // 14 days in milliseconds
    private StorageInventoryDB inventoryDB;

    public CacheExpiryCheck(StorageInventoryDB inventoryDB) {
        this.inventoryDB = inventoryDB;
    }

    /**
     * Checks whether a cache object has expired based on its last modified time and removes it if expired.
     * An object is considered expired if it has been in the cache for more than two weeks.
     *
     * @param co The CacheObject to be checked for expiry.
     * @throws IntegrityException if the cache object's last modified time is unknown.
     * @throws StorageVolumeException if there is an issue removing the expired object from the cache volume.
     */
    @Override
    public void check(CacheObject co) throws IntegrityException, StorageVolumeException {
        long currentTime = System.currentTimeMillis();
        long objectLastModifiedTime = co.getLastModified();

        // Throw an exception if the last modified time is unknown
        if (objectLastModifiedTime == -1) {
            throw new IntegrityException("Last modified time of cache object is unknown: " + co.name);
        }

        // If the cache object is expired, remove it from the cache
        if ((currentTime - objectLastModifiedTime) > TWO_WEEKS_MILLIS) {
            removeExpiredObject(co);
        }
    }

    /**
     * Removes an expired object from the cache.
     *
     * @param co The expired CacheObject to be removed.
     * @throws StorageVolumeException if there is an issue removing the object from the cache volume.
     */
    protected void removeExpiredObject(CacheObject co) throws StorageVolumeException {
        CacheVolume volume = co.volume;
        if (volume != null && volume.remove(co.name)) {
            try {
                inventoryDB.removeObject(co.volname, co.name);
            } catch (InventoryException e) {
                throw new StorageVolumeException("Failed to remove object from inventory database: " + co.name, e);
            }
        } else {
            throw new StorageVolumeException("Failed to remove expired object from cache volume: " + co.name);
        }
    }
}
