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

import java.util.Collection;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class EnvVarIncludesWordsTest {

    EnvVarIncludesWords rule = null;

    @Test
    public void testWordsInVal() {
        String val = "quick brown fox";
        Collection<String> words = EnvVarIncludesWords.wordsInVal(val);
        assertTrue("words missing quick", words.contains("quick"));
        assertTrue("words missing brown", words.contains("brown"));
        assertTrue("words missing fox", words.contains("fox"));

        words = EnvVarIncludesWords.wordsInVal(null);
        assertEquals(0, words.size());

        words = EnvVarIncludesWords.wordsInVal("");
        assertFalse("contains empty string", words.contains(""));
        assertEquals(0, words.size());
    }

    @Test
    public void testWordsInEnv() {
        Collection<String> words = EnvVarIncludesWords.wordsInEnv("USER");
        assertEquals(1, words.size());

        words = EnvVarIncludesWords.wordsInEnv("OAR_GOOBER");
        assertEquals(0, words.size());
    }

    @Test
    public void testSatisfied() {
        // Note: can't assume an environment variable is set to a particular value
        rule = new EnvVarIncludesWords("OAR_GOOBER", "quick", "brown", "fox");
        assertFalse(rule.satisfied());

        rule = new EnvVarIncludesWords("USER", "gurn");
        assertFalse(rule.satisfied());
    }
}
