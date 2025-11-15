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
 *
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.inventory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import gov.nist.oar.distrib.cachemgr.InventoryException;

/**
 * Unit tests for PostgresStorageInventoryDB
 *
 * NOTE: These tests require a PostgreSQL database to be available.
 * To run these tests, you need to:
 * 1. Have a PostgreSQL server running (e.g., via Docker: docker run --name postgres-test -e POSTGRES_PASSWORD=test -p 5432:5432 -d postgres)
 * 2. Set the environment variable POSTGRES_TEST_URL with the JDBC connection URL
 *    Example: export POSTGRES_TEST_URL="//localhost:5432/postgres?user=postgres&password=test"
 *
 * If the environment variable is not set, these tests will be skipped.
 */
public class PostgresStorageInventoryDBTest {

    private static String testDbUrl;
    private static boolean dbAvailable = false;

    @BeforeAll
    public static void checkDatabaseAvailability() {
        testDbUrl = System.getenv("POSTGRES_TEST_URL");
        if (testDbUrl != null && !testDbUrl.isEmpty()) {
            // Try to connect to verify the database is actually available
            try {
                String jdbcUrl = testDbUrl.startsWith("jdbc:postgresql:") ? testDbUrl : "jdbc:postgresql:" + testDbUrl;
                Connection conn = DriverManager.getConnection(jdbcUrl);
                conn.close();
                dbAvailable = true;
                System.out.println("PostgreSQL test database is available at: " + testDbUrl);
            } catch (SQLException e) {
                System.out.println("PostgreSQL test database configured but not reachable: " + e.getMessage());
                System.out.println("PostgreSQL tests will be skipped.");
            }
        } else {
            System.out.println("POSTGRES_TEST_URL not set. PostgreSQL tests will be skipped.");
            System.out.println("To enable PostgreSQL tests, set POSTGRES_TEST_URL environment variable.");
            System.out.println("Example: export POSTGRES_TEST_URL='//localhost:5432/postgres?user=postgres&password=test'");
        }
    }

    @Test
    public void testInitializeDB() throws InventoryException {
        assumeTrue(dbAvailable, "PostgreSQL test database not available");

        // Initialize the database schema
        PostgresStorageInventoryDB.initializeDB(testDbUrl);

        // Verify we can create an instance
        PostgresStorageInventoryDB db = new PostgresStorageInventoryDB(testDbUrl);
        assertNotNull(db);
    }

    @Test
    public void testCreateInstance() throws InventoryException, SQLException {
        assumeTrue(dbAvailable, "PostgreSQL test database not available");

        // Ensure schema is initialized
        PostgresStorageInventoryDB.initializeDB(testDbUrl);

        // Create an instance
        PostgresStorageInventoryDB db = new PostgresStorageInventoryDB(testDbUrl);
        assertNotNull(db);

        // Verify we can connect
        Connection conn = db.connect();
        assertNotNull(conn);
        assertTrue(conn.isValid(5));
        conn.close();
    }

    @Test
    public void testRegisterAlgorithm() throws InventoryException {
        assumeTrue(dbAvailable, "PostgreSQL test database not available");

        PostgresStorageInventoryDB.initializeDB(testDbUrl);
        PostgresStorageInventoryDB db = new PostgresStorageInventoryDB(testDbUrl);

        // Register an algorithm
        db.registerAlgorithm("sha256");

        // Verify it was registered
        assertTrue(db.checksumAlgorithms().contains("sha256"));
    }

    @Test
    public void testRegisterVolume() throws InventoryException {
        assumeTrue(dbAvailable, "PostgreSQL test database not available");

        PostgresStorageInventoryDB.initializeDB(testDbUrl);
        PostgresStorageInventoryDB db = new PostgresStorageInventoryDB(testDbUrl);

        // Register a volume
        db.registerVolume("test-volume", 1000000000L, null);

        // Verify it was registered
        assertTrue(db.volumes().contains("test-volume"));
    }
}
