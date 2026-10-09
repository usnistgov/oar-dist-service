/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 */
package gov.nist.oar.distrib.cachemgr.pdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.ConfigurableCache;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.storage.FilesystemCacheVolume;

/**
 * Demonstrates the data collision that occurs when two HeadBag caches (public and RPA)
 * share the same inventory database, as would happen with PostgreSQL when only a single
 * {@code dburl} is configured.
 *
 * <h3>Background</h3>
 * With SQLite, each cache naturally gets its own database file:
 * <ul>
 *   <li>Data cache: {@code {admindir}/data.sqlite}</li>
 *   <li>HeadBag cache: {@code {admindir}/headbags/inventory.sqlite}</li>
 *   <li>RPA HeadBag cache: {@code {admindir}/rpaHeadbags/inventory.sqlite}</li>
 * </ul>
 * With PostgreSQL, if only {@code distrib.cachemgr.dburl} is set (and {@code headbagDburl}
 * / {@code rpaDburl} are left unset), all three caches fall back to the same JDBC URL.
 * They then share the same {@code objects}, {@code volumes}, and {@code algorithms} tables.
 *
 * <h3>The problem</h3>
 * Both {@code NISTCacheManagerConfig.createHeadBagManager()} and
 * {@code RPACachingServiceProvider.createHeadBagManager()} hardcode volume names
 * "cv0" and "cv1". When these two caches share a database:
 * <ol>
 *   <li>The second cache's {@code registerVolume("cv0", ...)} silently overwrites
 *       the first cache's volume capacity</li>
 *   <li>Objects added by one cache are visible to the other's queries</li>
 *   <li>Deletion planning, space calculations, and dataset summaries mix data
 *       from both caches</li>
 * </ol>
 *
 * <h3>This test proves</h3>
 * <ol>
 *   <li>Volume capacity overwrite when second cache registers same name</li>
 *   <li>Cross-cache object leakage via findObject() and selectObjectsByPDRID()</li>
 *   <li>Corrupted space calculations (available space wrong for both caches)</li>
 *   <li>summarizeContents() merging data from both caches</li>
 * </ol>
 *
 * <h3>How to read this test</h3>
 * Each assertion here is written to PASS when the collision happens: the messages are labeled
 * "BUG" on purpose. The point of the test is to prove the hazard is real and so justify the
 * startup guard in {@code NISTCacheManagerConfig.resolvePostgresAwareInventoryDburl} that refuses
 * a shared PostgreSQL inventory. It is NOT asserting desired behavior. If the head-bag caches are
 * ever changed to use distinct volume names or otherwise isolate their data, these assertions will
 * start to fail; that would be expected, and the test should then be updated or removed rather than
 * treated as a regression.
 */
public class SharedDatabaseCollisionTest {

    @TempDir
    File tempDir;

    /** A single shared database — simulates a shared PostgreSQL instance */
    private String sharedDbPath;

    /** Public headbag cache DB handle */
    private HeadBagDB publicDb;

    /** RPA headbag cache DB handle */
    private HeadBagDB rpaDb;

    @BeforeEach
    void setUp() throws IOException, InventoryException {
        // Create ONE database file — the equivalent of both caches pointing to the same
        // PostgreSQL URL (e.g., jdbc:postgresql://localhost:5432/cache_db)
        File dbFile = new File(tempDir, "shared_inventory.sqlite");
        sharedDbPath = dbFile.getAbsolutePath();
        PDRStorageInventoryDB.initializeSQLiteDB(sharedDbPath);

        // Both caches get their own HeadBagDB Java object, but they point to the SAME database.
        // This is exactly what happens when headbagDburl and rpaDburl both fall back to dburl.
        publicDb = HeadBagDB.createHeadBagDB(sharedDbPath);
        rpaDb = HeadBagDB.createHeadBagDB(sharedDbPath);
    }

    /**
     * SCENARIO 1: Volume capacity overwrite.
     *
     * NISTCacheManagerConfig.createHeadBagManager() creates volumes "cv0" and "cv1"
     * with capacity = headbagCacheSize / 2 (e.g., 50MB).
     *
     * RPACachingServiceProvider.createHeadBagManager() creates "cv0" and "cv1"
     * with capacity = rpa.headbagCacheSize / 2 (e.g., 25MB).
     *
     * Since registerVolume() UPDATEs if the name already exists, the second
     * registration silently overwrites the first's capacity.
     */
    @Test
    void testVolumeCapacityOverwrite() throws InventoryException {
        publicDb.registerAlgorithm("sha256");

        // Public headbag cache registers cv0 with 50MB capacity
        long publicCapacity = 50_000_000L;
        publicDb.registerVolume("cv0", publicCapacity, null);

        JSONObject info = publicDb.getVolumeInfo("cv0");
        assertEquals(publicCapacity, info.getLong("capacity"),
                "Public cache set cv0 capacity to 50MB");

        // RPA headbag cache starts up and registers cv0 with 25MB capacity.
        // Since the volume name already exists, registerVolume() UPDATEs it.
        long rpaCapacity = 25_000_000L;
        rpaDb.registerVolume("cv0", rpaCapacity, null);

        // The public cache's capacity has been silently overwritten
        info = publicDb.getVolumeInfo("cv0");
        assertEquals(rpaCapacity, info.getLong("capacity"),
                "BUG: Public cache's cv0 capacity was silently overwritten by RPA cache");
        assertNotEquals(publicCapacity, info.getLong("capacity"),
                "The original 50MB capacity is gone — replaced by RPA's 25MB");
    }

    /**
     * SCENARIO 2: Cross-cache object leakage.
     *
     * Objects added by the public headbag cache appear in RPA cache queries
     * and vice versa, because both read from the same objects table.
     */
    @Test
    void testCrossCacheObjectLeakage() throws InventoryException {
        // Both caches register algorithm and the same volume names (as the production code does)
        publicDb.registerAlgorithm("sha256");
        publicDb.registerVolume("cv0", 50_000_000L, null);

        rpaDb.registerAlgorithm("sha256");
        rpaDb.registerVolume("cv0", 25_000_000L, null);

        // --- Public headbag cache stores a public dataset's head bag ---
        JSONObject publicMd = new JSONObject();
        publicMd.put("size", 1000L);
        publicMd.put("pdrid", "ark:/88434/mds2-2106");
        publicMd.put("ediid", "ark:/88434/mds2-2106");
        publicDb.addObject(
                "mds2-2106.1_0_0.mbag0_4-3",  // objid: public head bag
                "cv0",                          // volume name
                "mds2-2106.1_0_0.mbag0_4-3.zip",
                publicMd
        );

        // --- RPA headbag cache stores a restricted dataset's head bag ---
        JSONObject rpaMd = new JSONObject();
        rpaMd.put("size", 2000L);
        rpaMd.put("pdrid", "ark:/88434/mds2-2909");
        rpaMd.put("ediid", "ark:/88434/mds2-2909");
        rpaDb.addObject(
                "mds2-2909.1_0_0.mbag0_4-0",  // objid: restricted head bag
                "cv0",                          // same volume name!
                "mds2-2909.1_0_0.mbag0_4-0.zip",
                rpaMd
        );

        // --- Now query the public DB for the public dataset ---
        List<CacheObject> publicResults = publicDb.selectObjectsByPDRID("ark:/88434/mds2-2106", 0);
        assertEquals(1, publicResults.size(), "Public bag found correctly");
        assertEquals("mds2-2106.1_0_0.mbag0_4-3.zip", publicResults.get(0).name);

        // --- BUG: Query the public DB for the RPA dataset — it finds it! ---
        List<CacheObject> leakedResults = publicDb.selectObjectsByPDRID("ark:/88434/mds2-2909", 0);
        assertEquals(1, leakedResults.size(),
                "BUG: Public headbag DB can see RPA-only restricted data bag");
        assertEquals("mds2-2909.1_0_0.mbag0_4-0.zip", leakedResults.get(0).name,
                "The restricted dataset's head bag leaked into the public cache's view");

        // --- Same in reverse: RPA DB sees the public head bag ---
        List<CacheObject> reverseLeaked = rpaDb.selectObjectsByPDRID("ark:/88434/mds2-2106", 0);
        assertEquals(1, reverseLeaked.size(),
                "BUG: RPA headbag DB can see public-only data bag");
    }

    /**
     * SCENARIO 3: Corrupted space calculations.
     *
     * When both caches add objects to "cv0", getAvailableSpaceIn("cv0") sums
     * ALL objects from both caches. Each cache thinks it has less space than
     * it actually does (in its own physical volume), or may think volume is full
     * when only the other cache filled it.
     */
    @Test
    void testCorruptedSpaceCalculations() throws InventoryException {
        publicDb.registerAlgorithm("sha256");
        publicDb.registerVolume("cv0", 10_000L, null);

        // Public cache adds 3000 bytes of data
        JSONObject md = new JSONObject();
        md.put("size", 3000L);
        md.put("pdrid", "ark:/88434/pub1");
        md.put("ediid", "pub1");
        publicDb.addObject("pub1/file1", "cv0", "pub1_file1.zip", md);

        assertEquals(7000L, publicDb.getAvailableSpaceIn("cv0"),
                "Before RPA: 10000 - 3000 = 7000 available");

        // RPA cache registers same volume (overwrites capacity to 10000 — same value here)
        rpaDb.registerAlgorithm("sha256");
        rpaDb.registerVolume("cv0", 10_000L, null);

        // RPA cache adds 5000 bytes of its own data
        md = new JSONObject();
        md.put("size", 5000L);
        md.put("pdrid", "ark:/88434/rpa1");
        md.put("ediid", "rpa1");
        rpaDb.addObject("rpa1/file1", "cv0", "rpa1_file1.zip", md);

        // BUG: Public cache now sees only 2000 bytes available — the RPA data
        // is counted against the public cache's space budget
        long available = publicDb.getAvailableSpaceIn("cv0");
        assertEquals(2000L, available,
                "BUG: Public cache thinks only 2000 bytes free (10000 - 3000 - 5000) " +
                "because RPA objects are counted against its space");

        // The public cache's physical volume only has 3000 bytes used,
        // so it should report 7000 available. But it reports 2000.
        assertNotEquals(7000L, available,
                "Public cache should have 7000 bytes free in its physical volume, but doesn't");
    }

    /**
     * SCENARIO 4: summarizeContents() merges data from both caches.
     *
     * A cache management API call to list what's cached returns datasets
     * from both the public and RPA caches, with no way to distinguish them.
     */
    @Test
    void testSummarizeContentsMergesData() throws InventoryException {
        publicDb.registerAlgorithm("sha256");
        publicDb.registerVolume("cv0", 50_000_000L, null);

        rpaDb.registerAlgorithm("sha256");
        rpaDb.registerVolume("cv0", 25_000_000L, null);

        // Public cache: 2 files from dataset "pub-dataset"
        JSONObject md = new JSONObject();
        md.put("size", 1000L);
        md.put("pdrid", "ark:/88434/pub-dataset");
        md.put("ediid", "ark:/88434/pub-dataset");
        publicDb.addObject("pub-dataset/file1", "cv0", "pub_file1.zip", md);
        md.put("size", 2000L);
        publicDb.addObject("pub-dataset/file2", "cv0", "pub_file2.zip", md);

        // RPA cache: 1 file from restricted dataset "rpa-dataset"
        md = new JSONObject();
        md.put("size", 5000L);
        md.put("pdrid", "ark:/88434/rpa-dataset");
        md.put("ediid", "ark:/88434/rpa-dataset");
        rpaDb.addObject("rpa-dataset/secret1", "cv0", "rpa_secret1.zip", md);

        // Query from the public cache's perspective
        org.json.JSONArray contents = publicDb.summarizeContents("cv0");

        // BUG: Returns 2 datasets instead of 1 — the RPA restricted dataset leaked in
        assertEquals(2, contents.length(),
                "BUG: summarizeContents sees 2 datasets — public + RPA mixed together");

        // Verify both datasets are present
        boolean foundPublic = false, foundRpa = false;
        for (int i = 0; i < contents.length(); i++) {
            JSONObject ds = contents.getJSONObject(i);
            if ("ark:/88434/pub-dataset".equals(ds.getString("ediid"))) {
                foundPublic = true;
                assertEquals(2, ds.getInt("filecount"));
                assertEquals(3000L, ds.getLong("totalsize"));
            }
            if ("ark:/88434/rpa-dataset".equals(ds.getString("ediid"))) {
                foundRpa = true;
                assertEquals(1, ds.getInt("filecount"));
                assertEquals(5000L, ds.getLong("totalsize"));
            }
        }
        assertTrue(foundPublic, "Public dataset found in summary");
        assertTrue(foundRpa,
                "BUG: Restricted RPA dataset appears in public cache summary — " +
                "an admin viewing public cache contents would see restricted datasets");
    }
}
