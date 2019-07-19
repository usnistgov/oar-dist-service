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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.datapackage.ValidationHelper;

public class ValidationHelperTest {

    @Test
    public void testIsAllowedURL() throws IOException {
	// this is what is currently set in production
	String allowed = "nist.gov|s3.amazonaws.com/nist-midas";

	assertTrue(ValidationHelper.isAllowedURL("https://nist.gov/datafile.dat", allowed));
	assertTrue(ValidationHelper.isAllowedURL("http://srd.nist.gov/srd13/datafile.dat", allowed));
	assertTrue(ValidationHelper.isAllowedURL("https://s3.amazonaws.com/nist-midas/bigbag.zip", allowed));
	assertTrue(ValidationHelper.isAllowedURL("http://srdnist.gov/srd13/datafile.dat", allowed));

	assertFalse("Don't allow the domain part appear anywhere in the URL path",
		ValidationHelper.isAllowedURL("http://example.com/nist.gov/anyolfile.exe", allowed));
	assertFalse("Pay attention to field boundaries",
		ValidationHelper.isAllowedURL("https://s3.amazonaws.com/nist-midas-games/doom.zip", allowed));
    }

    @Test
    public void testGetUrlStatus() throws IOException {
	String domains = "nist.gov|s3.amazonaws.com/nist-midas|httpstat.us";
	String testurlError = "http://httpstat.us/404";
	String testUrlRedirect = "http://www.nist.gov/srd/srd_data/srd13_B-049.json";
//	ValidationHelper validationHelper = new ValidationHelper();
	URLStatusLocation urlLoc = ValidationHelper.getFileURLStatusSize(testurlError, domains,1);
	assertEquals(urlLoc.getStatus(), 404);

	urlLoc = ValidationHelper.getFileURLStatusSize(testUrlRedirect, domains,1);
	assertEquals(urlLoc.getStatus(), 301);

    }

    @Test
    public void testUrlCode() {
	String expectedMessage = "The requested file by given URL is not found on server.";
	String message = ValidationHelper.getStatusMessage(404);
	assertEquals(message, expectedMessage);
	expectedMessage = "The given URL is malformed.";
	message = ValidationHelper.getStatusMessage(400);
	assertEquals(message, expectedMessage);
    }

}
