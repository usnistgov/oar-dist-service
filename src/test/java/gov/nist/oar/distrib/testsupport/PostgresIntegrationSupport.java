package gov.nist.oar.distrib.testsupport;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresIntegrationSupport {

    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("oar_test")
            .withUsername("postgres")
            .withPassword("postgres");

    private static boolean started = false;

    @BeforeAll
    void ensurePostgresContainer() {
        assumeTrue(isDockerAvailable(), "Docker is not available for PostgreSQL integration tests");
        if (! started) {
            POSTGRES.start();
            started = true;
        }
    }

    protected boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        }
        catch (Throwable ex) {
            return false;
        }
    }

    protected String uniqueName(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().replace("-", ""))
            .toLowerCase(Locale.ROOT);
    }

    protected String createDatabase(String prefix) throws SQLException {
        String db = uniqueName(prefix);
        try (Connection conn = DriverManager.getConnection(adminJdbcUrl());
             Statement stmt = conn.createStatement())
        {
            stmt.execute("CREATE DATABASE " + db);
        }
        return db;
    }

    protected String createSchema(String database, String prefix) throws SQLException {
        String schema = uniqueName(prefix);
        try (Connection conn = DriverManager.getConnection(jdbcUrl(database));
             Statement stmt = conn.createStatement())
        {
            stmt.execute("CREATE SCHEMA " + schema);
        }
        return schema;
    }

    protected String adminJdbcUrl() {
        return jdbcUrl("postgres");
    }

    protected String jdbcUrl(String database) {
        return String.format("jdbc:postgresql://%s:%d/%s?user=%s&password=%s",
                             POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), database,
                             POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    protected String jdbcUrl(String database, String schema) {
        return jdbcUrl(database) + "&currentSchema=" + schema;
    }

    protected void executeSql(String jdbcUrl, String... statements) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement())
        {
            for (String sql : statements)
                stmt.execute(sql);
        }
    }

    protected int queryForInt(String jdbcUrl, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql))
        {
            rs.next();
            return rs.getInt(1);
        }
    }

    protected List<String> queryForStrings(String jdbcUrl, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql))
        {
            List<String> out = new ArrayList<String>();
            while (rs.next())
                out.add(rs.getString(1));
            return out;
        }
    }
}
