package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.web.ConfigurationException;
import gov.nist.oar.distrib.web.RPAConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RPAConfigurationTest {

    RPAConfiguration rpaConfiguration = null;

    @Before
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
