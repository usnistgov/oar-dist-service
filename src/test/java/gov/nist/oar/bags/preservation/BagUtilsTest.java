/*
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.bags.preservation;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import gov.nist.oar.bags.preservation.BagUtils;
import gov.nist.oar.ds.service.impl.DownloadServiceImpl;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.ParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class BagUtilsTest {
    
    Logger logger = LoggerFactory.getLogger(BagUtilsTest.class);
    public BagUtilsTest() {}

    @Test
    public void testIsLegalBagName() {
        assertTrue(BagUtils.isLegalBagName("goober.mbag0_3-0"));
        assertTrue(BagUtils.isLegalBagName("goober.mbag0_2-2"));
        assertTrue(BagUtils.isLegalBagName("goober.mbag0_3-12"));
        assertTrue(BagUtils.isLegalBagName("goober.mbag0_3-13.zip"));
        assertTrue(BagUtils.isLegalBagName("goober.mbag10_22-13.tar.gz"));
        assertTrue(BagUtils.isLegalBagName("6376FC675D0E1D77E0531A5706812BC21886.mbag10_22-13.tar.gz"));
        ///Test bagname with bagversion version
        assertTrue(BagUtils.isLegalBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13.zip"));
        assertTrue(BagUtils.isLegalBagName("6376FC675D0E1D77E0531A5706812BC21886.67.mbag10_22-13.tar.gz"));
        
        assertFalse(BagUtils.isLegalBagName("go.ober.mbag10_22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.mbag10.22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.mbag10_22+13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.multibag10_22-13.tar.gz"));
        //Test the names with bagversion
        assertFalse(BagUtils.isLegalBagName("go.ober.9.mbag10_22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.10_34.mbag10.22-13.tar.gz"));
        
        
    }
    @Test
    public void testParseNewBagName() throws ParseException{
     
      logger.info("TEST Leagal parsing :"+BagUtils.isLegalBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13.zip"));
      logger.info("TEST Legal parsing :"+BagUtils.isLegalBagName("6376FC675D0E1D77E0531A5706812BC21886.mbag10_22-13.zip"));
      ArrayList<String> need = new ArrayList<String>(Arrays.asList("6376FC675D0E1D77E0531A5706812BC21886","02","10_22","13","zip"));
      ArrayList<String> test = (ArrayList<String>) BagUtils.parseBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13.zip");
      
      assertEquals(need,test);
      
      need.set(4, "");
      assertEquals(need, BagUtils.parseBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13"));
      need.set(4, "tar.gz");
      assertEquals(need, BagUtils.parseBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13.tar.gz"));
    }
    
    @Test
    public void testParseBagName() throws ParseException {
        ArrayList<String> need = new ArrayList<String>(Arrays.asList("goober","0_3","0","zip"));

        assertEquals(need, BagUtils.parseBagName("goober.mbag0_3-0.zip"));

        need.set(3, "");
        assertEquals(need, BagUtils.parseBagName("goober.mbag0_3-0"));

        need.set(2, "2");
        assertEquals(need, BagUtils.parseBagName("goober.mbag0_3-2"));

        need.set(1, "0_2");
        assertEquals(need, BagUtils.parseBagName("goober.mbag0_2-2"));

        need.set(1, "10_223");
        assertEquals(need, BagUtils.parseBagName("goober.mbag10_223-2"));

        need.set(3, "tar.gz");
        assertEquals(need, BagUtils.parseBagName("goober.mbag10_223-2.tar.gz"));

        need.set(0, "691DDF3315711C14E0532457068146BE1907");
        assertEquals(need,
                     BagUtils.parseBagName("691DDF3315711C14E0532457068146BE1907.mbag10_223-2.tar.gz"));
        
        
    }
    
    @Test
    public void testParseBagNameFail() {
        try {
            BagUtils.parseBagName("go.ober.mbag10_22-13.tar.gz");
            fail("Failed to throw ParseException");
        } catch (ParseException ex) {  }
        
        try {
            BagUtils.parseBagName("goober.mbag10.22-13.tar.gz"); 
            fail("Failed to throw ParseException");
        }
        catch (ParseException ex) { }
        
        try {
            BagUtils.parseBagName("goober.mbag10_22+13"); 
            fail("Failed to throw ParseException");
        } 
        catch (ParseException ex) {  }
        
        try {
            BagUtils.parseBagName("goober.multibag10_22-13"); 
            fail("Failed to throw ParseException");
        } 
        catch (ParseException ex) {  }
    }

    @Test
    public void testVersionComparater() {
        Comparator<String> vc = BagUtils.versionComparator();
        assertEquals( 0, vc.compare("0_2", "0_2"));
        assertTrue("Compare not negative", vc.compare("0_2", "0_3") < 0);
        assertTrue("Compare not positive", vc.compare("0_3", "0_2") > 0);

        assertTrue("Compare not negative", vc.compare("1.2.3", "1.2.4") < 0);
        assertTrue("Compare not positive", vc.compare("1.5.3", "1.2.3") > 0);
        assertTrue("Compare not negative", vc.compare("1.5.3", "2.2.3") < 0);
        assertTrue("Compare not negative", vc.compare("0", "3") < 0);
        assertTrue("Compare not positive", vc.compare("3", "0") > 0);
    }

  
    @Test
    public void testNameCompare() {
        Comparator<String> vc = BagUtils.bagNameComparator();
        assertTrue("Compare not positive", vc.compare("goober.mbag0_2-4", "goober.mbag0_2-0") > 0);
        assertTrue("Compare not negative", vc.compare("goober.mbag0_2-0", "goober.mbag0_3-0") < 0);
        assertTrue("Compare not negative", vc.compare("goober.mbag0_2-4", "gurn.mbag0_2-0") < 0);
        assertTrue("Compare not positive", vc.compare("goober.mbag0_2-1", "goober.mbag0_3-0") > 0);
        assertTrue("Compare not negative",
                   vc.compare("goober.mbag0_2-0.7z", "goober.mbag0_2-0.zip") < 0);
        assertEquals( 0, vc.compare("goober.mbag0_2-0.7z", "goober.mbag0_2-0.7z"));
    }

    @Test
    public void testFindLastHeadBag() {
        ArrayList<String> names = new ArrayList<String>(4);
        names.add("goober.mbag0_2-0");
        assertEquals("goober.mbag0_2-0", BagUtils.findLatestHeadBag(names));

        names.add("goober.mbag0_3-0");
        assertEquals("goober.mbag0_3-0", BagUtils.findLatestHeadBag(names));
        
        names.add(0, "goober.mbag0_2-13");
        assertEquals("goober.mbag0_2-13", BagUtils.findLatestHeadBag(names));
        
        names.add(1, "goober.mbag0_2-3.zip");
        assertEquals("goober.mbag0_2-13", BagUtils.findLatestHeadBag(names));
        
        names.add("goober.mbag1_2-13.7z");
        assertEquals("goober.mbag1_2-13.7z", BagUtils.findLatestHeadBag(names));
    }
    
    @Test
    public void testBagVersionComparater() {
        Comparator<String> vc = BagUtils.bagVersionComparator();
        assertEquals( 0, vc.compare("testname123.04.mbag10_22-13", "testname123.04.mbag10_22-13"));
        assertTrue("Compare not negative", vc.compare("testname123.04.mbag10_22-13.zip", "testname123.05.mbag10_22-13.zip") < 0);
        assertTrue("Compare not negative", vc.compare("testname123.04.mbag10_22-13.zip", "testname123.05.mbag10_22-11.zip") < 0);
        assertTrue("Compare not negative", vc.compare("testname123.mbag10_22-13.zip", "testname123.mbag10_22-14.zip") < 0);
        assertTrue("Compare not negative", vc.compare("testname123.mbag10_42-13", "testname123.mbag10_22-14") > 0);

        assertTrue("Compare not negative", vc.compare("1.3.3", "1.2.4") > 0);
        assertTrue("Compare not positive", vc.compare("1.5.3", "1.2.3") > 0);

       assertTrue("Compare not negative", vc.compare("0", "3") < 0);
       assertTrue("Compare not positive", vc.compare("3", "0") > 0);
    }
    
    @Test
    public void testFindLastHeadBagWithBagVersion() {
        ArrayList<String> names = new ArrayList<String>(4);
        names.add("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-14.zip");
       
        //assertEquals("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13", BagUtils.findLatestHeadBagWithBagVersion(names));

        names.add("6376FC675D0E1D77E0531A5706812BC21886.04.mbag10_22-15.zip");
        names.add("6376FC675D0E1D77E0531A5706812BC21886.03.mbag10_22-16.zip");
        names.add("6376FC675D0E1D77E0531A5706812BC21886.04.mbag10_22-12.zip");
        names.add("6376FC675D0E1D77E0531A5706812BC21886.03.mbag10_22-13.zip");
logger.info("TSE::"+BagUtils.findLatestHeadBagWithBagVersion(names).trim());
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.04.mbag10_22-15.zip", BagUtils.findLatestHeadBagWithBagVersion(names));
    }
}

