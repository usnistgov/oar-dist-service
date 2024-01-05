package gov.nist.oar.distrib.web;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

public class RPAConfigurationTest {

    @Test
    public void testLoadConfig() throws IOException {
        RPAConfiguration config = 
            (new ObjectMapper()).readValue(getClass().getResourceAsStream("/rpaconfig.json"),
                                           RPAConfiguration.class);
        assertEquals("https://localhost/od/ds", config.getBaseDownloadUrl());
        assertEquals("https://localhost/datacart/rpa", config.getDatacartUrl());
        assertEquals(50000000L, config.getHeadbagCacheSize());
        assertEquals("local", config.getBagstoreMode());
        assertNull(config.getBagstoreLocation());
        assertEquals("1234567890 pdr.rpa.2023 1234567890", config.getJwtSecretKey());
    }
}
