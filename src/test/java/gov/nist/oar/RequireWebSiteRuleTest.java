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
package gov.nist.oar;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class RequireWebSiteRuleTest {

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
}