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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.datapackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gov.nist.oar.RequireWebSite;

public class ValidationHelperTest {

    RequireWebSite required = new RequireWebSite("http://httpstat.us/200");

    @BeforeEach
    public void setUp() {
        // Initialization if needed before each test
    }

    @Test
    public void testIsAllowedURL() throws IOException {
        String allowed = "nist.gov|s3.amazonaws.com/nist-midas";

        assertTrue(ValidationHelper.isAllowedURL("https://nist.gov/datafile.dat", allowed));
        assertTrue(ValidationHelper.isAllowedURL("http://srd.nist.gov/srd13/datafile.dat", allowed));
        assertTrue(ValidationHelper.isAllowedURL("https://s3.amazonaws.com/nist-midas/bigbag.zip", allowed));
        assertTrue(ValidationHelper.isAllowedURL("http://srdnist.gov/srd13/datafile.dat", allowed));

        assertFalse(ValidationHelper.isAllowedURL("http://example.com/nist.gov/anyolfile.exe", allowed),
			"Don't allow the domain part appear anywhere in the URL path");
        assertFalse(ValidationHelper.isAllowedURL("https://s3.amazonaws.com/nist-midas-games/doom.zip", allowed),
			"Pay attention to field boundaries");
    }

    @Test
    public void testGetUrlStatus() throws IOException {
        assumeTrue(required.checkSite());

        String domains = "nist.gov|s3.amazonaws.com/nist-midas|httpstat.us";
        String testurlError = "http://httpstat.us/404";
        String testUrlRedirect = "http://www.nist.gov/srd/srd_data/srd13_B-049.json";

        URLStatusLocation urlLoc = ValidationHelper.getFileURLStatusSize(testurlError, domains, 1);
        assertEquals(404, urlLoc.getStatus());

        urlLoc = ValidationHelper.getFileURLStatusSize(testUrlRedirect, domains, 1);
        assertEquals(301, urlLoc.getStatus());
    }

    @Test
    public void testUrlCode() {
        String expectedMessage = "The requested file by given URL is not found on server.";
        String message = ValidationHelper.getStatusMessage(404);
        assertEquals(expectedMessage, message);

        expectedMessage = "The given URL is malformed.";
        message = ValidationHelper.getStatusMessage(400);
        assertEquals(expectedMessage, message);
    }
}
