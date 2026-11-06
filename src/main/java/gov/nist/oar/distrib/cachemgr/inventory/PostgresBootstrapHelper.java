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
package gov.nist.oar.distrib.cachemgr.inventory;

import gov.nist.oar.distrib.cachemgr.InventoryException;
import gov.nist.oar.distrib.cachemgr.InventorySearchException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the storage-inventory tables in a PostgreSQL database by running a bundled SQL script.
 * <p>
 * This is the PostgreSQL version of the inventory setup (SQLite has its own). Several service
 * instances can share one PostgreSQL inventory and may start up at the same time, so the
 * table-creating script is run while holding a PostgreSQL "advisory lock": only one instance runs
 * it at a time, and the others wait their turn and then find the tables already there (the script
 * uses CREATE TABLE IF NOT EXISTS). This prevents two instances from creating the same tables at
 * once.
 * </p>
 */
public final class PostgresBootstrapHelper {

    // These two statements take and release a PostgreSQL "advisory lock": a named lock the
    // application chooses, not tied to any row or table. The lock id is a number derived (via
    // hashtext) from the database name, the schema, and a lock name, so every instance computes
    // the SAME id and therefore blocks the others. pg_advisory_lock waits until it gets the lock;
    // pg_advisory_unlock frees it. The '?' is the lock name, bound at run time.
    private static final String LOCK_SQL =
        "SELECT pg_advisory_lock(hashtext(current_database() || ':' || coalesce(current_schema(), 'public') || ':' || ?))";
    private static final String UNLOCK_SQL =
        "SELECT pg_advisory_unlock(hashtext(current_database() || ':' || coalesce(current_schema(), 'public') || ':' || ?))";

    private PostgresBootstrapHelper() { }   // utility class: not meant to be instantiated

    /**
     * Create the inventory tables in a PostgreSQL database by running a bundled SQL script, making
     * sure only one instance does it at a time.
     *
     * @param jdbcUrl        the PostgreSQL database to set up, as a JDBC URL
     * @param resourceOwner  the class used to locate the SQL script on the classpath
     * @param resourcePath   where the SQL script lives on the classpath (its statements are
     *                       separated by ';')
     * @param lockName       a name for the advisory lock; instances using the same name take turns
     * @throws InventoryException if the script cannot be read or one of its statements fails
     */
    public static void initializeSchema(String jdbcUrl, Class<?> resourceOwner, String resourcePath, String lockName)
        throws InventoryException {
        // read the whole SQL script (from the classpath) into one string
        StringBuilder sb = new StringBuilder();
        InputStream stream = resourceOwner.getResourceAsStream(resourcePath);
        if (stream == null)
            throw new InventoryException("Problem reading db init script: missing resource " + resourcePath);

        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(stream))) {
            String line = null;
            while ((line = rdr.readLine()) != null)
                sb.append(" ").append(line);
        }
        catch (IOException ex) {
            throw new InventoryException("Problem reading db init script: " + ex.getMessage(), ex);
        }

        // the script is one or more statements separated by ';'; run them one at a time
        String[] stmts = sb.toString().split(";");
        String connUrl = jdbcUrl.startsWith("jdbc:postgresql:") ? jdbcUrl : "jdbc:postgresql:" + jdbcUrl;

        Connection conn = null;
        boolean locked = false;
        try {
            conn = DriverManager.getConnection(connUrl);
            // take the advisory lock so another instance starting at the same time waits here
            // until we have finished creating the tables
            locked = acquireLock(conn, lockName);
            for (String s : stmts) {
                s = s.trim();
                if (s.isEmpty()) continue;   // skip blank pieces left by the split
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(s);
                }
                catch (SQLException ex) {
                    throw new InventorySearchException("DB init SQL statement failed: " + s + ":\n"
                                                       + ex.getMessage(), ex);
                }
            }
        }
        catch (SQLException ex) {
            throw new InventorySearchException("Failed to connect to PostgreSQL database, " + jdbcUrl + ": "
                                               + ex.getMessage(), ex);
        }
        finally {
            // always release the lock and close the connection. Even if releasing fails, closing
            // the connection drops the lock anyway, because an advisory lock belongs to the session.
            if (conn != null) {
                if (locked) {
                    try {
                        releaseLock(conn, lockName);
                    }
                    catch (SQLException ex) { /* closing the connection below releases it anyway */ }
                }
                try { conn.close(); } catch (SQLException ex) { }
            }
        }
    }

    /** Take the advisory lock, waiting until it becomes available; returns true once it is held. */
    private static boolean acquireLock(Connection conn, String lockName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(LOCK_SQL)) {
            stmt.setString(1, lockName);
            stmt.execute();
            return true;
        }
    }

    /** Release the advisory lock taken by {@link #acquireLock}. */
    private static void releaseLock(Connection conn, String lockName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UNLOCK_SQL)) {
            stmt.setString(1, lockName);
            stmt.execute();
        }
    }
}
