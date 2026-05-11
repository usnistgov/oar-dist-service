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

public final class PostgresBootstrapHelper {

    private static final String LOCK_SQL =
        "SELECT pg_advisory_lock(hashtext(current_database() || ':' || coalesce(current_schema(), 'public') || ':' || ?))";
    private static final String UNLOCK_SQL =
        "SELECT pg_advisory_unlock(hashtext(current_database() || ':' || coalesce(current_schema(), 'public') || ':' || ?))";

    private PostgresBootstrapHelper() { }

    public static void initializeSchema(String jdbcUrl, Class<?> resourceOwner, String resourcePath, String lockName)
        throws InventoryException {
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

        String[] stmts = sb.toString().split(";");
        String connUrl = jdbcUrl.startsWith("jdbc:postgresql:") ? jdbcUrl : "jdbc:postgresql:" + jdbcUrl;

        Connection conn = null;
        boolean locked = false;
        try {
            conn = DriverManager.getConnection(connUrl);
            locked = acquireLock(conn, lockName);
            for (String s : stmts) {
                s = s.trim();
                if (s.isEmpty()) continue;
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
            if (conn != null) {
                if (locked) {
                    try {
                        releaseLock(conn, lockName);
                    }
                    catch (SQLException ex) { /* try ignoring */ }
                }
                try { conn.close(); } catch (SQLException ex) { }
            }
        }
    }

    private static boolean acquireLock(Connection conn, String lockName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(LOCK_SQL)) {
            stmt.setString(1, lockName);
            stmt.execute();
            return true;
        }
    }

    private static void releaseLock(Connection conn, String lockName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UNLOCK_SQL)) {
            stmt.setString(1, lockName);
            stmt.execute();
        }
    }
}
