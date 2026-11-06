package gov.nist.oar.distrib.cachemgr.pdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.testsupport.PostgresIntegrationSupport;

public class PostgresPDRRuntimeIT extends PostgresIntegrationSupport {

    private PDRStorageInventoryDB newPdrDb() throws SQLException, InventoryException {
        String db = createDatabase("pdr");
        String url = jdbcUrl(db);
        PDRStorageInventoryDB.initializePostgresDB(url);
        PDRStorageInventoryDB sidb = PDRStorageInventoryDB.createPostgresDB(url);
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);
        return sidb;
    }

    private HeadBagDB newHeadbagDb() throws SQLException, InventoryException {
        String db = createDatabase("headbag");
        String url = jdbcUrl(db);
        HeadBagDB.initializePostgresDB(url);
        HeadBagDB sidb = HeadBagDB.createPostgresDB(url);
        sidb.registerAlgorithm("sha256");
        sidb.registerVolume("foobar", 450000, null);
        sidb.registerVolume("fundrum", 450000, null);
        return sidb;
    }

    @Test
    public void testSelectorAndSummaryQueriesOnPostgres() throws Exception {
        PDRStorageInventoryDB sidb = newPdrDb();

        JSONObject md = new JSONObject();
        md.put("priority", 4);
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "ark:/88888/abcd");
        sidb.addObject("1234/goober.json", "foobar", "1234_goober.json", md);
        md.put("size", 544L);
        sidb.addObject("1234/gurn.json", "fundrum", "1234_gurn.json", md);
        md.put("pdrid", "ark:/88888/2345");
        md.put("ediid", "2345");
        sidb.addObject("2345/goober.json", "fundrum", "2345_goober.json", md);

        List<CacheObject> pdr = sidb.selectObjectsByPDRID("ark:/88888/1234", 0);
        assertEquals(2, pdr.size());

        List<CacheObject> edi = sidb.selectObjectsByEDIID("ark:/88888/abcd", 0);
        assertEquals(2, edi.size());

        JSONObject vol = sidb.getVolumeTotals("fundrum");
        assertEquals(2L, vol.getLong("filecount"));
        assertEquals(1088L, vol.getLong("totalsize"));

        JSONObject ds = sidb.summarizeDataset("1234");
        assertNotNull(ds);
        assertEquals(1000L, ds.getLong("totalsize"));
        assertEquals(2L, ds.getLong("filecount"));
        assertEquals("ark:/88888/1234", ds.getString("pdrid"));
        assertEquals("ark:/88888/abcd", ds.getString("ediid"));

        JSONArray contents = sidb.summarizeContents(null);
        assertEquals(2, contents.length());
        HashMap<String, JSONObject> summaries = new HashMap<String, JSONObject>();
        for (int i = 0; i < contents.length(); i++) {
            JSONObject row = contents.getJSONObject(i);
            summaries.put(row.getString("aipid"), row);
        }
        assertEquals("ark:/88888/1234", summaries.get("abcd").getString("pdrid"));
        assertEquals("ark:/88888/2345", summaries.get("2345").getString("pdrid"));
    }

    @Test
    public void testHeadBagLookupOnPostgres() throws Exception {
        HeadBagDB sidb = newHeadbagDb();

        JSONObject md = new JSONObject();
        md.put("size", 456L);
        md.put("pdrid", "ark:/88888/1234");
        md.put("ediid", "1234");
        sidb.addObject("1234.1_0_0.mbag0_4-3", "foobar", "1234.1_0_0.mbag0_4-3.zip", md);
        sidb.addObject("1234.1_23_0.mbag0_4-85", "foobar", "1234.1_23_0.mbag0_4-85.zip", md);

        List<CacheObject> cos = sidb.findHeadBag("ark:/88888/1234", 0);
        assertEquals(1, cos.size());
        assertEquals("1234.1_23_0.mbag0_4-85", cos.get(0).id);
    }

    @Test
    public void testOverlappingSummaryQueriesAndWrites() throws Exception {
        String db = createDatabase("overlap");
        String url = jdbcUrl(db);
        PDRStorageInventoryDB.initializePostgresDB(url);

        PDRStorageInventoryDB writerDb = PDRStorageInventoryDB.createPostgresDB(url);
        writerDb.registerAlgorithm("sha256");
        writerDb.registerVolume("foobar", 450000, null);

        PDRStorageInventoryDB readerDb = PDRStorageInventoryDB.createPostgresDB(url);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        tasks.add(() -> {
            for (int i = 0; i < 20; i++) {
                JSONObject md = new JSONObject();
                md.put("priority", 4);
                md.put("size", 10L + i);
                md.put("pdrid", "ark:/88888/1234");
                md.put("ediid", "edi-1234");
                writerDb.addObject("1234/file-" + i + ".json", "foobar", "file-" + i + ".json", md);
            }
            return null;
        });
        tasks.add(() -> {
            for (int i = 0; i < 30; i++) {
                readerDb.summarizeContents(null);
                readerDb.summarizeDataset("1234");
                readerDb.selectObjectsByPDRID("ark:/88888/1234", 0);
            }
            return null;
        });

        List<Future<Void>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        for (Future<Void> future : futures)
            future.get();

        JSONObject summary = readerDb.summarizeDataset("1234");
        assertNotNull(summary);
        assertEquals(20L, summary.getLong("filecount"));
        assertTrue(summary.getLong("totalsize") > 0L);
    }
}
