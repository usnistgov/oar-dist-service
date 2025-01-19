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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

public class BagUtilsTest {
    
    // Logger logger = LoggerFactory.getLogger(BagUtilsTest.class);
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
        assertTrue(BagUtils.isLegalBagName("6376FC675D0E1D77E0531A5706812BC21886.67_3_199.mbag10_22-13.tar.gz"));
        assertTrue(BagUtils.isLegalBagName("pdr19-1886.67_3_199.mbag10_22-13.tar.gz"));
        
        assertFalse(BagUtils.isLegalBagName("go.ober.mbag10_22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.mbag10.22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.mbag10_22+13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.multibag10_22-13.tar.gz"));
        //Test the names with bagversion
        assertFalse(BagUtils.isLegalBagName("go.ober.9.mbag10_22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("goober.10_34.mbag10.22-13.tar.gz"));
        assertFalse(BagUtils.isLegalBagName("-pdr19-1886.67_3_199.mbag10_22-13.tar.gz"));
        
        
    }
    @Test
    public void testParseNewBagName() throws ParseException{
     
      ArrayList<String> need =
          new ArrayList<String>(Arrays.asList("6376FC675D0E1D77E0531A5706812BC21886","1_2_3_15","10_22","13","zip"));
      ArrayList<String> test = (ArrayList<String>)
          BagUtils.parseBagName("6376FC675D0E1D77E0531A5706812BC21886.1_2_3_15.mbag10_22-13.zip");
      
      assertEquals(need,test);
      
      need.set(4,   "");
      need.set(1, "02");
      assertEquals(need, BagUtils.parseBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13"));
      need.set(4, "tar.gz");
      assertEquals(need, BagUtils.parseBagName("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-13.tar.gz"));
    }

    @Test
    public void testSequenceNumberIn() {
        assertEquals(0, BagUtils.sequenceNumberIn("goober.mbag0_3-0.zip"));
        assertEquals(0, BagUtils.sequenceNumberIn("goober.mbag0_3-0"));
        assertEquals(2, BagUtils.sequenceNumberIn("goober.mbag0_3-2"));
        assertEquals(2, BagUtils.sequenceNumberIn("goober.1_0_3.mbag0_4-2"));
        assertEquals(223, BagUtils.sequenceNumberIn("goober.mbag10_2-223"));
        assertEquals(21, BagUtils.sequenceNumberIn("goober.1_81_413.mbag10_223-21.tar.gz"));
        assertEquals(-1, BagUtils.sequenceNumberIn("6376FC675D0E1D77E0531A5706812BC21886"));
        assertEquals(-1, BagUtils.sequenceNumberIn("goober.mbag10_2-test"));
    }

    @Test
    public void test_make_nameverre() {
        String base = "^(\\w[\\w\\-]*)\\.";
        assertEquals(base+"1(_0)*\\.", BagUtils._make_nameverre("1.0.0.0").pattern());
        assertEquals(base+"1(_0)*\\.", BagUtils._make_nameverre("1.0.0").pattern());
        assertEquals(base+"1(_0)*\\.", BagUtils._make_nameverre("1.0").pattern());
        assertEquals(base+"1(_0)*\\.", BagUtils._make_nameverre("1").pattern());

        assertEquals(base+"2_1(_0)*\\.", BagUtils._make_nameverre("2.1.0").pattern());
        assertEquals(base+"2_1(_0)*\\.", BagUtils._make_nameverre("2.1").pattern());

        assertEquals(base+"1_3_4(_0)*\\.", BagUtils._make_nameverre("1.3.4").pattern());
        assertEquals(base+"1_0_4(_0)*\\.", BagUtils._make_nameverre("1.0.4").pattern());
        
        assertEquals(base+"0(_0)*\\.", BagUtils._make_nameverre("0.0.0.0").pattern());
    }
    
    @Test
    public void testMatchesVersion() {
        assertTrue(BagUtils.matchesVersion("goober.1_0_0.mbag0_3-0.zip", "1.0.0"));
        assertTrue(BagUtils.matchesVersion("goober.1_0_0.mbag0_3-0", "1.0_0"));
        assertTrue(BagUtils.matchesVersion("goober.1_0.mbag0_3-0", "1_0_0"));
        assertTrue(BagUtils.matchesVersion("goober.1_0_0_0.mbag0_3-0", "1.0"));
        assertTrue(! BagUtils.matchesVersion("goober.2_0.mbag0_3-0", "1.0.0"));
        assertTrue(! BagUtils.matchesVersion("goober.1_0_0.mbag0_3-0", "1.0.2"));
        assertTrue(! BagUtils.matchesVersion("goober.1_0_0.mbag0_3-0", "2.0.2"));
        
        assertTrue(BagUtils.matchesVersion("goober.mbag0_2-0", ""));
        assertTrue(BagUtils.matchesVersion("goober.mbag0_2-2", "1"));
        assertTrue(BagUtils.matchesVersion("goober.1.mbag0_2-2", "1"));
        assertTrue(BagUtils.matchesVersion("goober.mbag0_2-2", "0"));
        assertTrue(BagUtils.matchesVersion("goober.mbag0_2-3", "1.0.0"));
        
        assertTrue(BagUtils.matchesVersion("goober.1_81_413.mbag10_223-21.tar.gz", "1.81.413.0"));
        assertTrue(! BagUtils.matchesVersion("6376FC675D0E1D77E0531A5706812BC21886", "1.0"));
    }
    
    @Test
    public void testParseBagName() throws ParseException {
        ArrayList<String> need = new ArrayList<String>(Arrays.asList("goober", "", "0_3","0","zip"));

        assertEquals(need, BagUtils.parseBagName("goober.mbag0_3-0.zip"));

        need.set(4, "");
        assertEquals(need, BagUtils.parseBagName("goober.mbag0_3-0"));

        need.set(3, "2");
        assertEquals(need, BagUtils.parseBagName("goober.mbag0_3-2"));

        need.set(2, "0_2");
        assertEquals(need, BagUtils.parseBagName("goober.mbag0_2-2"));

        need.set(2, "10_223");
        assertEquals(need, BagUtils.parseBagName("goober.mbag10_223-2"));

        need.set(4, "tar.gz");
        assertEquals(need, BagUtils.parseBagName("goober.mbag10_223-2.tar.gz"));

        need.set(1, "1_81_413");
        assertEquals(need, BagUtils.parseBagName("goober.1_81_413.mbag10_223-2.tar.gz"));

        need.set(1, "3");
        need.set(0, "691DDF3315711C14E0532457068146BE1907");
        assertEquals(need,
                     BagUtils.parseBagName("691DDF3315711C14E0532457068146BE1907.3.mbag10_223-2.tar.gz"));
        
        
    }

    @Test
    public void testMultibagVersionOf() {
        assertEquals("0.3", BagUtils.multibagVersionOf("goober.mbag0_3-0.zip"));
        assertEquals("1.4", BagUtils.multibagVersionOf("goober.mbag1_4-0.zip"));
        assertEquals("", BagUtils.multibagVersionOf("goober.0.zip"));
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
        assertTrue(vc.compare("0_2", "0_3") < 0, "Compare not negative");
        assertTrue(vc.compare("0_3", "0_2") > 0, "Compare not positive");
        assertTrue(vc.compare("1.2.3", "1.2.4") < 0, "Compare not negative");
        assertTrue(vc.compare("1.5.3", "1.2.3") > 0, "Compare not positive");
        assertTrue(vc.compare("1.5.3", "2.2.3") < 0, "Compare not negative");
        assertTrue(vc.compare("0", "3") < 0, "Compare not negative");
        assertTrue(vc.compare("3", "0") > 0, "Compare not positive");
        assertTrue(vc.compare("3", "3_1") < 0, "Compare not negative");
        assertTrue(vc.compare("3", "") > 0, "Compare not positive");
        assertTrue(vc.compare("", "3_1") < 0, "Compare not negative");
        assertTrue(vc.compare("3_0", "3") == 0, "Compare equal");
        assertTrue(vc.compare("3", "3_0_0") == 0, "Compare equal");
        assertTrue(vc.compare("", "") == 0, "Compare equal");

    }

  
    @Test
    public void testNameCompare() {
        Comparator<String> vc = BagUtils.bagNameComparator();
        assertTrue(vc.compare("goober.mbag0_2-4", "goober.mbag0_2-0") > 0, "Compare not positive");
        assertTrue(vc.compare("goober.mbag0_2-0", "goober.mbag0_3-0") < 0, "Compare not negative");
        assertTrue(vc.compare("goober.mbag0_2-4", "gurn.mbag0_2-0") < 0, "Compare not negative");
        assertTrue(vc.compare("goober.mbag0_2-1", "goober.mbag0_3-0") > 0, "Compare not positive");
        assertTrue(vc.compare("goober.mbag0_2-0.7z", "goober.mbag0_2-0.zip") < 0, "Compare not negative");

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
    public void testFindLastHeadBagWithBagVersion() {
        ArrayList<String> names = new ArrayList<String>(4);

        // empty list
        try {
            String nm = BagUtils.findLatestHeadBag(names);
            fail("Failed barf at empty bag list; returned "+nm);
        } catch (IllegalArgumentException ex) { }

        // single-element list
        names.add("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-14.zip");
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-14.zip",
                     BagUtils.findLatestHeadBag(names));

        // seq 14 > 12
        names.add("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_23-12.zip");
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.02.mbag10_22-14.zip",
                     BagUtils.findLatestHeadBag(names));

        // seq 15 > 14 (despite 10_24 > 10_23)
        names.add("6376FC675D0E1D77E0531A5706812BC21886.mbag10_24-15.zip");
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag10_24-15.zip",
                     BagUtils.findLatestHeadBag(names));

        // seq 15 > 13 (despite no version provided)
        names.add("6376FC675D0E1D77E0531A5706812BC21886.04.mbag10_22-12.zip");
        names.add("6376FC675D0E1D77E0531A5706812BC21886.03.mbag10_22-13.zip");
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.mbag10_24-15.zip",
                     BagUtils.findLatestHeadBag(names));

        // version 4_2 > 0
        names.add("6376FC675D0E1D77E0531A5706812BC21886.4_2.mbag10_22-15.zip");
        assertEquals("6376FC675D0E1D77E0531A5706812BC21886.4_2.mbag10_22-15.zip",
                     BagUtils.findLatestHeadBag(names));

        names.add("goober-1.zip");
        try {
            String nm = BagUtils.findLatestHeadBag(names);
            fail("Failed barf at illegal name in list; returned "+nm);
        } catch (IllegalArgumentException ex) { }
    }

    @Test
    public void testSelectVersion() {
        ArrayList<String> names = new ArrayList<String>(8);
        names.add("goober.mbag0_2-0");
        names.add("goober.0_1.mbag0_2-0");
        names.add("goober.0_1.mbag0_2-1");
        names.add("goober.2.mbag0_2-1");
        names.add("goober.3_1_15.mbag0_2-1");
        names.add("goober.3_1_15.mbag0_2-2");
        names.add("goober.3_1_15.mbag0_4-3");
        names.add("goober.4_0.mbag0_2-1");

        List<String> mtchd = BagUtils.selectVersion(names, "0.1");
        assertEquals("goober.0_1.mbag0_2-0", mtchd.get(0));
        assertEquals("goober.0_1.mbag0_2-1", mtchd.get(1));
        assertEquals(2, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "0_1");
        assertEquals("goober.0_1.mbag0_2-0", mtchd.get(0));
        assertEquals("goober.0_1.mbag0_2-1", mtchd.get(1));
        assertEquals(2, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "0.1.0");
        assertEquals("goober.0_1.mbag0_2-0", mtchd.get(0));
        assertEquals("goober.0_1.mbag0_2-1", mtchd.get(1));
        assertEquals(2, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "2.0.0");
        assertEquals("goober.2.mbag0_2-1", mtchd.get(0));
        assertEquals(1, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "0");
        assertEquals("goober.mbag0_2-0", mtchd.get(0));
        assertEquals(1, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "1");
        assertEquals("goober.mbag0_2-0", mtchd.get(0));
        assertEquals(1, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "3.1.15");
        assertEquals("goober.3_1_15.mbag0_2-1", mtchd.get(0));
        assertEquals("goober.3_1_15.mbag0_2-2", mtchd.get(1));
        assertEquals("goober.3_1_15.mbag0_4-3", mtchd.get(2));
        assertEquals(3, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "3.1");
        assertEquals(0, mtchd.size());
    }
    
    @Test
    public void testSelectVersionDash() {
        ArrayList<String> names = new ArrayList<String>(8);
        names.add("go-ober.mbag0_2-0");
        names.add("go-ober.0_1.mbag0_2-0");
        names.add("go-ober.0_1.mbag0_2-1");
        names.add("go-ober.2.mbag0_2-1");
        names.add("go-ober.3_1_15.mbag0_2-1");
        names.add("go-ober.3_1_15.mbag0_2-2");
        names.add("go-ober.3_1_15.mbag0_4-3");
        names.add("go-ober.4_0.mbag0_2-1");

        List<String> mtchd = BagUtils.selectVersion(names, "0.1");
        assertTrue( mtchd.size() > 0, "Failed to match a version: 0.1");
        assertEquals("go-ober.0_1.mbag0_2-0", mtchd.get(0));
        assertEquals("go-ober.0_1.mbag0_2-1", mtchd.get(1));
        assertEquals(2, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "0_1");
        assertTrue( mtchd.size() > 0, "Failed to match a version: 0_1");
        assertEquals("go-ober.0_1.mbag0_2-0", mtchd.get(0));
        assertEquals("go-ober.0_1.mbag0_2-1", mtchd.get(1));
        assertEquals(2, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "0.1.0");
        assertTrue( mtchd.size() > 0, "Failed to match a version: 0.1.0");
        assertEquals("go-ober.0_1.mbag0_2-0", mtchd.get(0));
        assertEquals("go-ober.0_1.mbag0_2-1", mtchd.get(1));
        assertEquals(2, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "2.0.0");
        assertTrue( mtchd.size() > 0, "Failed to match a version: 2.0.0");
        assertEquals("go-ober.2.mbag0_2-1", mtchd.get(0));
        assertEquals(1, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "0");
        assertTrue( mtchd.size() > 0, "Failed to match a the unspecified version: (0)");
        assertEquals("go-ober.mbag0_2-0", mtchd.get(0));
        assertEquals(1, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "1");
        assertTrue( mtchd.size() > 0, "Failed to match a version: 1");
        assertEquals("go-ober.mbag0_2-0", mtchd.get(0));
        assertEquals(1, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "3.1.15");
        assertTrue( mtchd.size() > 0, "Failed to match a version: 3.1.15");
        assertEquals("go-ober.3_1_15.mbag0_2-1", mtchd.get(0));
        assertEquals("go-ober.3_1_15.mbag0_2-2", mtchd.get(1));
        assertEquals("go-ober.3_1_15.mbag0_4-3", mtchd.get(2));
        assertEquals(3, mtchd.size());

        mtchd = BagUtils.selectVersion(names, "3.1");
        assertEquals(0, mtchd.size(), "Matched a non-existent version");
    }
}

