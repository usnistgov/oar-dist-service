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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;

import org.junit.jupiter.api.Test;


public class EnvVarIncludesWordsTest {

    EnvVarIncludesWords rule = null;

    @Test
    public void testWordsInVal() {
        String val = "quick brown fox";
        Collection<String> words = EnvVarIncludesWords.wordsInVal(val);
        assertTrue(words.contains("quick"), () -> "words missing quick");
        assertTrue(words.contains("brown"), () -> "words missing brown");
        assertTrue(words.contains("fox"), () -> "words missing fox");

        words = EnvVarIncludesWords.wordsInVal(null);
        assertEquals(0, words.size());

        words = EnvVarIncludesWords.wordsInVal("");
        assertFalse(words.contains(""), () -> "contains empty string");
        assertEquals(0, words.size());
    }

    @Test
    public void testWordsInEnv() {
        assertNotNull("JAVA_HOME is not set as an environment var; consider changing this test",
                      System.getenv("JAVA_HOME"));
        Collection<String> words = EnvVarIncludesWords.wordsInEnv("JAVA_HOME");
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
