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
 */
package gov.nist.oar.distrib;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class RequireWebSiteTest {

    RequireWebSite rule = null;

    @Test
    public void testCheckDownSite() {
        rule = new RequireWebSite("https://oardev3.nist.gov/");
        assertFalse(rule.checkSite());
    }

    @Test
    public void testCheckBadURLSite() {
        rule = new RequireWebSite("ivo://cds/");
        assertFalse(rule.checkSite());
    }

    @Test
    public void testCheckGoodSite() {
        rule = new RequireWebSite("https://google.com/");
        assumeTrue(rule.checkSite());
        assertTrue(rule.checkSite());
    }
}


