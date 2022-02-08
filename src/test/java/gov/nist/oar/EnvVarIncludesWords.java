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

import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import org.junit.runner.Description;
import org.junit.AssumptionViolatedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a JUnit ClassRule that requires that particular fields be found as words in the 
 * a specified environment variable (like OAR_TEST_INCLUDE)
 */
public class EnvVarIncludesWords implements TestRule {

    private String envvar = null;
    private HashSet<String> need = null;
    private Logger log = LoggerFactory.getLogger(getClass());

    /**
     * create the rule
     * @param envVarName    the name of the environment variable to look for words in
     * @param neededVals    the values to require for the rule to be satisfied.  Values
     *                      must not contain spaces; values that do will be ignored.
     */
    public EnvVarIncludesWords(String envVarName, String... neededVals) {
        envvar = envVarName;
        need = new HashSet<String>(neededVals.length);
        for (String word : neededVals) {
            need.add(word);
        }
    }

    public static Collection<String> wordsInEnv(String envVarName) {
        String val = System.getenv(envVarName);
        return wordsInVal(val);
    }

    public static Collection<String> wordsInVal(String val) {
        if (val == null)
            return new HashSet<String>();
        val = val.trim();
        if (val.length() == 0)
            return new HashSet<String>();

        String[] words = val.split(" ");
        HashSet<String> out = new HashSet<String>(words.length);
        for (String word : words) 
            out.add(word);
        return out;
    }

    public boolean satisfied() {
        Collection<String> got = wordsInEnv(envvar);
        return got.containsAll(need);
    }

    public Statement apply(Statement base, Description desc) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (! satisfied()) 
                    throw new AssumptionViolatedException("Site appears unavailable: Skipping test(s)");
                else
                    base.evaluate();
            }
        };
    }
}
