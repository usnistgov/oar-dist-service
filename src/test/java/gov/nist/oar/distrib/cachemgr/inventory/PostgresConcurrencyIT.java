package gov.nist.oar.distrib.cachemgr.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.testsupport.PostgresIntegrationSupport;
import gov.nist.oar.distrib.web.ConfigurationException;
import gov.nist.oar.distrib.web.NISTCacheManagerConfig;

public class PostgresConcurrencyIT extends PostgresIntegrationSupport {

    @TempDir
    Path tempDir;

    private NISTCacheManagerConfig newConfig(String dbUrl) throws IOException {
        File rootdir = tempDir.resolve(uniqueName("cache")).toFile();
        rootdir.mkdirs();

        NISTCacheManagerConfig cfg = new NISTCacheManagerConfig();
        cfg.setAdmindir(rootdir.toString());
        cfg.setDburl(dbUrl);

        List<NISTCacheManagerConfig.CacheVolumeConfig> vols =
            new ArrayList<NISTCacheManagerConfig.CacheVolumeConfig>();
        NISTCacheManagerConfig.CacheVolumeConfig vcfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        vcfg.setCapacity(2000L);
        File vdir = new File(rootdir, "vols/king");
        vdir.mkdirs();
        vcfg.setLocation("file://" + vdir);
        vcfg.setStatus("update");
        vcfg.setName("king");
        vols.add(vcfg);
        cfg.setVolumes(vols);

        return cfg;
    }

    @Test
    public void testConcurrentAlgorithmAndVolumeRegistrationIsIdempotent() throws Exception {
        String db = createDatabase("race");
        String url = jdbcUrl(db);
        PostgresStorageInventoryDB.initializeDB(url);

        PostgresStorageInventoryDB db1 = new PostgresStorageInventoryDB(url);
        PostgresStorageInventoryDB db2 = new PostgresStorageInventoryDB(url);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = List.of(
            () -> { db1.registerAlgorithm("sha256"); db1.registerVolume("cv0", 100L, null); return null; },
            () -> { db2.registerAlgorithm("sha256"); db2.registerVolume("cv0", 200L, null); return null; }
        );

        for (Future<Void> future : pool.invokeAll(tasks))
            future.get();
        pool.shutdown();

        assertEquals(1, queryForInt(url, "SELECT count(*) FROM algorithms WHERE name='sha256'"));
        assertEquals(1, queryForInt(url, "SELECT count(*) FROM volumes WHERE name='cv0'"));
    }

    @Test
    public void testSimultaneousCacheStartupAgainstSameDatabase() throws Exception {
        String db = createDatabase("startup_race");
        String url = jdbcUrl(db);
        NISTCacheManagerConfig cfg1 = newConfig(url);
        NISTCacheManagerConfig cfg2 = newConfig(url);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<BasicCache>> tasks = List.of(
            () -> cfg1.createDefaultCache(null),
            () -> cfg2.createDefaultCache(null)
        );

        for (Future<BasicCache> future : pool.invokeAll(tasks)) {
            BasicCache cache = future.get();
            assertNotNull(cache);
        }
        pool.shutdown();

        assertEquals(1, queryForInt(url, "SELECT count(*) FROM algorithms WHERE name='sha256'"));
        assertEquals(1, queryForInt(url, "SELECT count(*) FROM volumes WHERE name='king'"));
    }

    @Test
    public void testStartupRetryAfterConnectionFailure()
        throws SQLException, IOException, ConfigurationException, CacheManagementException {
        String db = createDatabase("retry_race");
        String goodUrl = jdbcUrl(db);
        NISTCacheManagerConfig bad = newConfig("jdbc:postgresql://127.0.0.1:1/nope?user=x&password=y");
        assertThrows(CacheManagementException.class, () -> bad.createDefaultCache(null));

        NISTCacheManagerConfig good = newConfig(goodUrl);
        assertNotNull(good.createDefaultCache(null));
    }
}
