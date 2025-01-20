package gov.nist.oar.distrib.service.rpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gov.nist.oar.distrib.web.RPAConfiguration;


public class RPAConfigurationTest {

    RPAConfiguration rpaConfiguration = null;

    @BeforeEach
    public void setUp() {
        rpaConfiguration = new RPAConfiguration();
    }

    @Test
    public void testGetRPAConfiguration_AuthorizedField() {
        assertNotNull(rpaConfiguration);
        assertNull(rpaConfiguration.getAuthorized());
        List<String> authorized = new ArrayList<>(Arrays.asList("Authorized_1", "Authorized_2"));
        rpaConfiguration.setAuthorized(authorized);
        assertEquals(2, rpaConfiguration.getAuthorized().size());
        assertEquals("Authorized_1", rpaConfiguration.getAuthorized().get(0));
        assertEquals("Authorized_2", rpaConfiguration.getAuthorized().get(1));
        assertTrue(rpaConfiguration.isAuthorized("Authorized_1"));
        assertFalse(rpaConfiguration.isAuthorized("Authorized_3"));
    }

}
