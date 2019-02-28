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
import java.net.MalformedURLException;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.oar.distrib.datapackage.ObjectUtils;

public class ObjectUtilsTest {

    @Test
    public void testValidateUrlDomain() throws IOException {
        // this is what is currently set in production
        String allowed = "nist.gov|s3.amazonaws.com/nist-midas";

        assertTrue(ObjectUtils.validateUrlDomain("https://nist.gov/datafile.dat", allowed));
        assertTrue(ObjectUtils.validateUrlDomain("http://srd.nist.gov/srd13/datafile.dat", allowed));
        assertTrue(ObjectUtils.validateUrlDomain("https://s3.amazonaws.com/nist-midas/bigbag.zip",
                                                 allowed));
        assertTrue(ObjectUtils.validateUrlDomain("http://srdnist.gov/srd13/datafile.dat", allowed));

        assertFalse("Don't allow the domain part appear anywhere in the URL path",
                    ObjectUtils.validateUrlDomain("http://example.com/nist.gov/anyolfile.exe",
                                                  allowed));
        assertFalse("Pay attention to field boundaries",
                    ObjectUtils.validateUrlDomain("https://s3.amazonaws.com/nist-midas-games/doom.zip",
                                                  allowed));
    }

}
