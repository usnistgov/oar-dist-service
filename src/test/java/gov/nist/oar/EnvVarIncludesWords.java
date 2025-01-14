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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.Assumptions;

/**
 * A JUnit 5 extension that requires that particular fields be found as words in the
 * a specified environment variable (like OAR_TEST_INCLUDE).
 */
public class EnvVarIncludesWords implements BeforeAllCallback {

    private String envvar = null;
    private Set<String> need = null;

    /**
     * Create the rule
     * @param envVarName    the name of the environment variable to look for words in
     * @param neededVals    the values to require for the rule to be satisfied.  Values
     *                      must not contain spaces; values that do will be ignored.
     */
    public EnvVarIncludesWords(String envVarName, String... neededVals) {
        this.envvar = envVarName;
        this.need = new HashSet<>(neededVals.length);
        for (String word : neededVals) {
            this.need.add(word);
        }
    }

    /**
     * Checks if the environment variable contains the required words.
     * 
     * @return true if all required words are found, false otherwise.
     */
    public boolean satisfied() {
        Collection<String> got = wordsInEnv(envvar);
        return got.containsAll(need);
    }

    /**
     * Extracts words from the specified environment variable.
     */
    public static Collection<String> wordsInEnv(String envVarName) {
        String val = System.getenv(envVarName);
        return wordsInVal(val);
    }

    /**
     * Splits the given string into words and returns a collection of them.
     */
    public static Collection<String> wordsInVal(String val) {
        if (val == null) return new HashSet<>();
        val = val.trim();
        if (val.isEmpty()) return new HashSet<>();

        String[] words = val.split(" ");
        Set<String> out = new HashSet<>(words.length);
        for (String word : words) {
            out.add(word);
        }
        return out;
    }

    /**
     * JUnit 5 callback that gets executed before all tests are run.
     * It assumes the test will be skipped if the required environment variable is not satisfied.
     */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Assumptions.assumeTrue(satisfied(), 
            "Environment variable '" + envvar + "' does not contain required values. Skipping test(s).");
    }
}
