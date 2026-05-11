package gov.nist.oar.distrib.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.BagStorage;
import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.cachemgr.pdr.HeadBagCacheManager;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.testsupport.PostgresIntegrationSupport;

public class PostgresRuntimeStartupIT extends PostgresIntegrationSupport {

    @TempDir
    Path tempDir;

    private NISTCacheManagerConfig newConfig(String dbUrl, String headbagUrl, String rpaUrl) throws IOException {
        File rootdir = tempDir.resolve(uniqueName("cache")).toFile();
        rootdir.mkdirs();

        NISTCacheManagerConfig cfg = new NISTCacheManagerConfig();
        cfg.setAdmindir(rootdir.toString());
        cfg.setDburl(dbUrl);
        cfg.setHeadbagDburl(headbagUrl);
        cfg.setRpaDburl(rpaUrl);

        List<NISTCacheManagerConfig.CacheVolumeConfig> vols =
            new ArrayList<NISTCacheManagerConfig.CacheVolumeConfig>();
        NISTCacheManagerConfig.CacheVolumeConfig vcfg = new NISTCacheManagerConfig.CacheVolumeConfig();
        vcfg.setCapacity(2000L);
        File vdir = new File(rootdir, "vols/king");
        vdir.mkdirs();
        vcfg.setLocation("file://" + vdir);
        vcfg.setStatus("update");
        List<String> roles = new ArrayList<String>();
        roles.add("general");
        vcfg.setRoles(roles);
        vcfg.setRedirectBase("http://data.nist.gov/cache/king");
        vcfg.setName("king");
        vols.add(vcfg);
        cfg.setVolumes(vols);

        return cfg;
    }

    private BagStorage makeBagStorage(String name) throws IOException {
        File bagDir = tempDir.resolve(name).toFile();
        bagDir.mkdirs();
        return new FilesystemLongTermStorage(bagDir.toString());
    }

    private RPAConfiguration makeRpaConfiguration() throws IOException {
        RPAConfiguration cfg = (new ObjectMapper()).readValue(
            getClass().getResourceAsStream("/rpaconfig.json"), RPAConfiguration.class);
        File bagDir = tempDir.resolve(uniqueName("rpalts")).toFile();
        bagDir.mkdirs();
        cfg.setBagstoreLocation(bagDir.toString());
        return cfg;
    }

    @Test
    public void testStartupWithExplicitSeparatedPostgresSchemas()
        throws IOException, SQLException, ConfigurationException, CacheManagementException
    {
        String db = createDatabase("startup");
        String mainSchema = createSchema(db, "maininv");
        String headSchema = createSchema(db, "headinv");
        String rpaSchema = createSchema(db, "rpainv");

        NISTCacheManagerConfig cfg = newConfig(jdbcUrl(db, mainSchema), jdbcUrl(db, headSchema),
                                               jdbcUrl(db, rpaSchema));
        BasicCache cache = cfg.createDefaultCache(null);
        HeadBagCacheManager headbags = cfg.createHeadBagManager(makeBagStorage("publts"));
        RPACachingServiceProvider prov =
            new RPACachingServiceProvider(cfg, makeRpaConfiguration(), makeBagStorage("publts-rpa"), null);
        HeadBagCacheManager rpaHeadbags = prov.getHeadBagCacheManager();

        assertNotNull(cache);
        assertNotNull(headbags);
        assertNotNull(rpaHeadbags);

        assertEquals(List.of("king"), queryForStrings(jdbcUrl(db), "SELECT name FROM " + mainSchema + ".volumes"));
        assertEquals(2, queryForStrings(jdbcUrl(db), "SELECT name FROM " + headSchema + ".volumes ORDER BY name").size());
        assertEquals(2, queryForStrings(jdbcUrl(db), "SELECT name FROM " + rpaSchema + ".volumes ORDER BY name").size());
    }

    @Test
    public void testPartialBootstrapRecoveryCreatesMissingTables()
        throws IOException, SQLException, ConfigurationException, CacheManagementException
    {
        String db = createDatabase("partial");
        String schema = createSchema(db, "recover");
        executeSql(jdbcUrl(db, schema),
                   "CREATE TABLE algorithms (id SERIAL PRIMARY KEY, name TEXT NOT NULL UNIQUE)");

        NISTCacheManagerConfig cfg = newConfig(jdbcUrl(db, schema), jdbcUrl(db, createSchema(db, "hb")),
                                               jdbcUrl(db, createSchema(db, "rpa")));
        BasicCache cache = cfg.createDefaultCache(null);
        assertNotNull(cache);
        assertTrue(queryForInt(jdbcUrl(db), "SELECT count(*) FROM information_schema.tables WHERE table_schema='" +
                               schema + "' AND table_name='objects'") == 1);
        assertTrue(queryForInt(jdbcUrl(db), "SELECT count(*) FROM information_schema.tables WHERE table_schema='" +
                               schema + "' AND table_name='volumes'") == 1);
    }

    @Test
    public void testMalformedSchemaFailsFast() throws IOException, SQLException {
        String db = createDatabase("broken");
        String schema = createSchema(db, "broken");
        executeSql(jdbcUrl(db, schema),
                   "CREATE TABLE algorithms (id SERIAL PRIMARY KEY)");

        NISTCacheManagerConfig cfg = newConfig(jdbcUrl(db, schema), jdbcUrl(db, createSchema(db, "hb")),
                                               jdbcUrl(db, createSchema(db, "rpa")));

        CacheManagementException ex = assertThrows(CacheManagementException.class, () -> {
            cfg.createDefaultCache(null);
        });

        assertTrue(ex.getMessage().contains("algorithm") || ex.getMessage().contains("name"));
    }

    @Test
    public void testFailedConnectionAndInvalidCredentialsFailFast() throws IOException, SQLException {
        String db = createDatabase("creds");
        String schema = createSchema(db, "good");
        String badPasswordUrl = String.format("jdbc:postgresql://%s:%d/%s?user=%s&password=wrong&currentSchema=%s",
                                              POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), db,
                                              POSTGRES.getUsername(), schema);

        NISTCacheManagerConfig badCreds = newConfig(badPasswordUrl, jdbcUrl(db, createSchema(db, "hb")),
                                                    jdbcUrl(db, createSchema(db, "rpa")));
        assertThrows(CacheManagementException.class, () -> badCreds.createDefaultCache(null));

        NISTCacheManagerConfig badHost = newConfig("jdbc:postgresql://127.0.0.1:1/nope?user=x&password=y",
                                                   "jdbc:postgresql://127.0.0.1:1/nope?user=x&password=y&currentSchema=hb",
                                                   "jdbc:postgresql://127.0.0.1:1/nope?user=x&password=y&currentSchema=rpa");
        assertThrows(CacheManagementException.class, () -> badHost.createDefaultCache(null));
    }

    @Test
    public void testStartupRetryBehaviorSucceedsAfterConfigurationFix()
        throws IOException, SQLException, ConfigurationException, CacheManagementException
    {
        String db = createDatabase("retry");
        String mainSchema = createSchema(db, "main");
        String hbSchema = createSchema(db, "hb");
        String rpaSchema = createSchema(db, "rpa");

        String badUrl = String.format("jdbc:postgresql://%s:%d/%s?user=%s&password=wrong&currentSchema=%s",
                                      POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), db,
                                      POSTGRES.getUsername(), mainSchema);
        NISTCacheManagerConfig cfg = newConfig(badUrl, jdbcUrl(db, hbSchema), jdbcUrl(db, rpaSchema));
        assertThrows(CacheManagementException.class, () -> cfg.createDefaultCache(null));

        cfg.setDburl(jdbcUrl(db, mainSchema));
        BasicCache cache = cfg.createDefaultCache(null);
        assertNotNull(cache);
    }
}
