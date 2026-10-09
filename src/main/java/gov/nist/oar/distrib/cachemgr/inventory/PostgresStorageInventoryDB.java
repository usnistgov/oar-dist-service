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
 */
package gov.nist.oar.distrib.cachemgr.inventory;

import gov.nist.oar.distrib.cachemgr.InventoryException;

/**
 * A <a href="https://www.postgresql.org/">PostgreSQL</a> implementation of a storage inventory database.
 * <p>
 * This implementation connects to a PostgreSQL database server using JDBC. The connection URL should
 * be in the format: {@code jdbc:postgresql://host:port/database}
 * </p>
 */
public class PostgresStorageInventoryDB extends JDBCStorageInventoryDB {

    /**
     * Initialize the class to access the database via a given JDBC URL.
     *
     * @param jdbcUrl the JDBC URL to connect to the PostgreSQL database, in the format
     *                {@code jdbc:postgresql://host:port/database?user=username&password=password}
     *                or just the host/database portion if credentials are provided separately
     */
    public PostgresStorageInventoryDB(String jdbcUrl) {
        // allow the URL to include the jdbc URL prefix or not
        super(jdbcUrl.startsWith("jdbc:postgresql:") ? jdbcUrl : "jdbc:postgresql:" + jdbcUrl);
    }

    /**
     * Create the necessary tables and indices for the storage inventory in a PostgreSQL database.
     *
     * @param jdbcUrl the JDBC URL to connect to the PostgreSQL database where the inventory
     *                tables should be created
     * @throws InventoryException if there is an error creating the tables
     */
    public static void initializeDB(String jdbcUrl) throws InventoryException {
        PostgresBootstrapHelper.initializeSchema(jdbcUrl, PostgresStorageInventoryDB.class,
                                                 "res/postgres_create.sql", "storage-inventory-bootstrap");
    }
}
