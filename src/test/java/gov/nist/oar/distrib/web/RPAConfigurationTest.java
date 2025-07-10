package gov.nist.oar.distrib.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RPAConfigurationTest {

    RPAConfiguration config;

    @BeforeEach
    public void setUp() throws IOException {
        config = new ObjectMapper().readValue(
                getClass().getResourceAsStream("/rpaconfig.json"),
                RPAConfiguration.class
        );
    }

    @Test
    public void testAuthorizedField() {
        assertNotNull(config);

        List<String> authorized = config.getAuthorized();
        assertNotNull(authorized);
        assertEquals(2, authorized.size());
        assertEquals("Authorized_1", authorized.get(0));
        assertEquals("Authorized_2", authorized.get(1));

        assertTrue(config.isAuthorized("Authorized_1"));
        assertFalse(config.isAuthorized("Unauthorized"));
    }

    @Test
    public void testLoadConfigAndBlacklist() {
        assertEquals("https://localhost/od/ds", config.getBaseDownloadUrl());
        assertEquals("https://localhost/datacart/rpa", config.getDatacartUrl());
        assertEquals(50000000L, config.getHeadbagCacheSize());
        assertEquals("local", config.getBagstoreMode());
        assertNull(config.getBagstoreLocation());
        assertEquals("1234567890 pdr.rpa.2023 1234567890", config.getJwtSecretKey());
        assertEquals(1209600000L, config.getExpiresAfterMillis());

        Map<String, RPAConfiguration.BlacklistConfig> blacklists = config.getBlacklists();
        assertNotNull(blacklists);
        assertTrue(blacklists.containsKey("ark:/88434/mds2-2909"));

        RPAConfiguration.BlacklistConfig blacklist = blacklists.get("ark:/88434/mds2-2909");
        assertNotNull(blacklist);

        List<String> emails = blacklist.getDisallowedEmails();
        assertNotNull(emails);
        assertEquals(2, emails.size());
        assertTrue(emails.contains("@123\\."));
        assertTrue(emails.contains("@example\\.com$"));

        List<String> countries = blacklist.getDisallowedCountries();
        assertNotNull(countries);
        assertEquals(2, countries.size());
        assertTrue(countries.contains("Cuba"));
        assertTrue(countries.contains("North Korea"));

        RPAConfiguration.EmailTemplate preApprovedEmail = config.preApprovedEmail();
        assertNotNull(preApprovedEmail);
        assertEquals("Your Data is Ready for Download", preApprovedEmail.getSubject());
        assertEquals(
            "<p>Hello ${FULL_NAME},<br><br>Your request for data related to <strong>${DATASET_NAME}</strong> has been processed.",
            preApprovedEmail.getContent()
        );

        RPAConfiguration.EmailTemplate failureTemplate = config.getEmailTemplates().get("failure-notification-user");
        assertNotNull(failureTemplate);
        assertEquals("Request Issue Notification", failureTemplate.getSubject());
        assertEquals(
                "There was an issue with your request for ${DATASET_NAME}. Please contact ${SUPPORT_EMAIL}.",
                failureTemplate.getContent());
        String supportEmail = config.getSupportEmail();
        assertEquals("rpa-support@nist.gov", supportEmail);
    }
}