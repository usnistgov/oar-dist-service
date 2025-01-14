package gov.nist.oar.distrib.web;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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

        // assertions for disallowed email strings and countries
        assertNotNull(config.getDisallowedEmails());
        assertEquals(2, config.getDisallowedEmails().size());
        assertTrue(config.getDisallowedEmails().contains("@123\\."));
        assertTrue(config.getDisallowedEmails().contains("@example\\.com$"));

        assertNotNull(config.getDisallowedCountries());
        assertEquals(2, config.getDisallowedCountries().size());
        assertTrue(config.getDisallowedCountries().contains("Cuba"));
        assertTrue(config.getDisallowedCountries().contains("North Korea"));
        assertEquals(1209600000L, config.getExpiresAfterMillis());

        // Validate pre-approved email template
        RPAConfiguration.EmailTemplate preApprovedEmail = config.preApprovedEmail();
        assertNotNull(preApprovedEmail);
        assertEquals("Your Data is Ready for Download", preApprovedEmail.getSubject());
        assertEquals(
            "<p>Hello ${FULL_NAME},<br><br>Your request for data related to <strong>${DATASET_NAME}</strong> has been processed.",
            preApprovedEmail.getContent()
        );
    }
}
