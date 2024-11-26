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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.RequireWebSite;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.datapackage.InputLimitException;
import gov.nist.oar.distrib.datapackage.BundleRequest;
import gov.nist.oar.distrib.datapackage.FileRequest;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DefaultDataPackagerTest {

    @ClassRule
    public static TestRule siterule =
        new RequireWebSite("https://s3.amazonaws.com/nist-midas/1894/license.pdf");
    
    private static long mxFileSize = 1000000;
    private static int numberofFiles = 100;
    private static String domains = "nist.gov|s3.amazonaws.com/nist-midas|httpstat.us";
    private static int redirectURLTrials = 1;
    private static FileRequest[] inputfileList = new FileRequest[2];
    private static BundleRequest bundleRequest;
    protected static Logger logger = LoggerFactory.getLogger(DefaultDataPackagerTest.class);
    private static DefaultDataPackager dp;
    private static String val1 = "";
    private static String val2 = "";
    Path path;
    ZipOutputStream zos;

    public static void createInput() throws JsonParseException, JsonMappingException, IOException {

	val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	createBundleRequest();
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
	createInput();
    }

    @Before
    public void construct() {
	dp = new DefaultDataPackager(bundleRequest, mxFileSize, numberofFiles, domains, redirectURLTrials);
    }

    @After
    public void closeAll() throws IOException {
	if (zos != null)
	    zos.close();
	if (path != null)
	    Files.delete(path);
    }

    @Test
    public void testSize() throws MalformedURLException, IOException {
	assertEquals(dp.getTotalSize(), 62562);
    }

    @Test
    public void testValidateRequest() throws DistributionException, IOException {
	val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	createBundleRequest();
	dp.validateBundleRequest();
	assertTrue(dp.getTotalSize() < mxFileSize);
    }

    @Test
    public void testValidateBundleRequest() throws DistributionException, IOException {
	val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	createBundleRequest();

	dp.validateBundleRequest();

	assertTrue(dp.getTotalSize() < mxFileSize);

    }

    @Test
    public void testGetData() throws DistributionException, MalformedURLException, IOException, InputLimitException {
        val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
        val2 = "{\"filePath\":\"/file.txt\",\"downloadUrl\":\"https://httpstat.us/404\"}";
        createBundleRequest();
        int expectedCount = 1;
        this.createBundleStream();
        dp.getData(zos);
        zos.close();
        // Initialize file count and process ZIP entries
        int actualCount = 0;
        try (ZipFile zipFile = new ZipFile(path.toString())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                System.out.println("entryname: " + entry.getName());

                // Validate the transformed file paths
                if (entry.getName().contains("license.pdf")) {
                    assertEquals("/1894/license.pdf", entry.getName()); // No transformation for short recordid
                } else if (entry.getName().equalsIgnoreCase("/DownloadErrors.txt")) {
                    continue; // Skip error file, if present
                }

                actualCount++; // Increment the count for valid entries
            }
        } catch (IOException ixp) {
            logger.error(
                    "There is an error while reading zip file contents in the getBundleZip test." + ixp.getMessage());
            throw ixp;
        }

        // Assert the number of valid entries in the ZIP file
        assertEquals(expectedCount, actualCount); // Expect exactly 2 valid files
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testValidateBundleRequestError() throws DistributionException, MalformedURLException, IOException {
	val1 = "{\"filePath\":\"/1894/license.pdf\",\"jgkdfghjkdf\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	exception.expect(JsonMappingException.class);
	createBundleRequest();
    }

    @Test
    public void testNoContentInPackageException() throws IOException, DistributionException {
	val1 = "{\"filePath\":\"/srd/srd13_B-049.json\",\"downloadUrl\":\"http://randomwww.nist.gov/random/testfile.json\"}";
	val2 = "{\"filePath\":\"/srd/srd13_B-050.json\",\"downloadUrl\":\"http://randomwww.nist.gov/random/testfile2.json\"}";
	createBundleRequest();
	this.createBundleStream();
	exception.expect(NoContentInPackageException.class);
	dp.getData(zos);
    }

    @Test
    public void testNoFilesAccesibleInPackageException() throws IOException, DistributionException {
	val1 = "{\"filePath\":\"/testfile1.txt\",\"downloadUrl\":\"https://httpstat.us/301\"}";
	val2 = "{\"filePath\":\"/testfile2.txt\",\"downloadUrl\":\"https://httpstat.us/301\"}";
	createBundleRequest();
	this.createBundleStream();
	exception.expect(NoFilesAccesibleInPackageException.class);
	dp.getData(zos);
    }

    private static void createBundleRequest() throws IOException {
	inputfileList = new FileRequest[2];
	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	inputfileList[0] = testval1;
	inputfileList[1] = testval2;
	bundleRequest = new BundleRequest("testdatabundle", inputfileList,0,2);
	dp = new DefaultDataPackager(bundleRequest, mxFileSize, numberofFiles, domains, redirectURLTrials);
    }

    private void createBundleStream() throws IOException {
	path = Files.createTempFile("testBundle", ".zip");
//	System.out.println("PATH:" + path);
	OutputStream os = Files.newOutputStream(path);
	zos = new ZipOutputStream(os);
    }

    public int checkFilesinZip(Path filepath) throws IOException {
	int count = 0;
	try (ZipFile file = new ZipFile(filepath.toString())) {
	    FileSystem fileSystem = FileSystems.getDefault();
	    // Get file entries
	    Enumeration<? extends ZipEntry> entries = file.entries();

	    // Iterate over entries
	    while (entries.hasMoreElements()) {

		ZipEntry entry = entries.nextElement();
		System.out.println("entryname:" + entry.getName());
		if (!entry.getName().equalsIgnoreCase("/DownloadErrors.txt"))
		    count++;

	    }

	    return count;

	} catch (IOException ixp) {
	    logger.error(
		    "There is an error while reading zip file contents in the getBundleZip test." + ixp.getMessage());
	    throw ixp;
	}
    }

    @Test
    public void testFormatLocation() {
        try {
            throw new IllegalArgumentException("test exception");
        }
        catch (IllegalArgumentException ex) {
            String msg = dp.formatLocation(ex, "testFormatLocation", this);
            System.out.println("### Detected exception thrown"+msg);
            assertNotEquals("  at undetected code location", msg);
            assertTrue("found wrong function: "+msg, msg.contains("testFormatLocation"));
        }

        try {
            throwDummy();
        }
        catch (IllegalArgumentException ex) {
            String msg = dp.formatLocation(ex, "testFormatLocation", this);
            System.out.println("### Detected exception thrown"+msg);
            assertNotEquals("  at undetected code location", msg);
            assertTrue("found wrong function: "+msg, msg.contains("testFormatLocation"));
        }

        try {
            throwDummy();
        }
        catch (IllegalArgumentException ex) {
            String msg = dp.formatLocation(ex, "throwDummy", this);
            System.out.println("### Detected exception thrown"+msg);
            assertNotEquals("  at undetected code location", msg);
            assertTrue("found wrong function: "+msg, msg.contains("throwDummy"));
        }

        try {
            throwDummy(1);
        }
        catch (IllegalArgumentException ex) {
            String msg = dp.formatLocation(ex, "throwDummy", this);
            System.out.println("### Detected exception thrown"+msg);
            assertNotEquals("  at undetected code location", msg);
            assertTrue("found wrong function: "+msg, msg.contains("throwDummy"));
        }

        try {
            throwDummy(4);
        }
        catch (IllegalArgumentException ex) {
            String msg = dp.formatLocation(ex, "throwDummy", this);
            System.out.println("### Detected exception thrown"+msg);
            assertNotEquals("  at undetected code location", msg);
            assertTrue("found wrong function: "+msg, msg.contains("throwDummy"));
        }
    }

    private void throwDummy() {
        throw new IllegalArgumentException("test exception");
    }
    private void throwDummy(int depth) {
        deepThrowDummy(depth);
    }
    private void deepThrowDummy(int depth) {
        if (depth < 2) throwDummy();
        deepThrowDummy(--depth);
    }
}
