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
package gov.nist.oar.distrib.service;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.InputLimitException;
import gov.nist.oar.distrib.datapackage.DefaultDataPackager;
import gov.nist.oar.distrib.web.objects.BundleRequest;
import gov.nist.oar.distrib.web.objects.FileRequest;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class DefaultDataPackagingServiceTest {

    private static Logger logger = LoggerFactory.getLogger(DefaultDataPackagingServiceTest.class);

    DefaultDataPackagingService ddp;
    static FileRequest[] requestedUrls = new FileRequest[2];
    long maxFileSize = 1000000;
    int numOfFiles = 100;
    String domains = "nist.gov|s3.amazonaws.com/nist-midas";
    static BundleRequest bundleRequest;

    public static void createRequest() throws JsonParseException, JsonMappingException, IOException {

	String val1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String val2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
	FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
	requestedUrls[0] = testval1;
	requestedUrls[1] = testval2;
	bundleRequest = new BundleRequest("testdatabundle", requestedUrls);
    }

    @BeforeClass
    public static void setUpClass() throws IOException {
	createRequest();
    }

    @Before
    public void setUp() {
	ddp = new DefaultDataPackagingService(domains, maxFileSize, numOfFiles);
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void getBundledZip() throws DistributionException, InputLimitException {
	try {
	    String bundleName = "example";
	    ddp.validateRequest(bundleRequest);
	    if (!bundleRequest.getBundleName().isEmpty() && bundleRequest.getBundleName() != null)
		bundleName = bundleRequest.getBundleName();

	    Path path = Files.createTempFile(bundleName, ".zip");
	    OutputStream os = Files.newOutputStream(path);
	    ZipOutputStream zos = new ZipOutputStream(os);
	    ddp.getBundledZipPackage(bundleRequest, zos);
	    zos.close();
	    int len = (int) Files.size(path);
	    System.out.println("Len:" + len);
	    // assertEquals(len, 59903);
	    assertNotNull(zos);
	    checkFilesinZip(path);
	    Files.delete(path);

	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}

    }

    public void checkFilesinZip(Path filepath) {
	int count = 0;
	try (ZipFile file = new ZipFile(filepath.toString())) {
	    FileSystem fileSystem = FileSystems.getDefault();
	    // Get file entries
	    Enumeration<? extends ZipEntry> entries = file.entries();

	    // ZipEntry entry = entries.nextElement();
	    // Just check first entry in zip
	    // assertEquals(entry.getName(), "/1894/license.pdf");
	    // Iterate over entries
	    while (entries.hasMoreElements()) {
		count++;
		ZipEntry entry = entries.nextElement();
		// if (entry.isDirectory())
		// System.out.println("entryname:"+entry.getName());
		if (count == 1)
		    assertEquals(entry.getName(), "/1894/license.pdf");
		if (count == 2)
		    assertEquals(entry.getName(), "/1894/license2.pdf");

	    }
	    assertEquals(count, 3);

	} catch (IOException ixp) {

	}
    }

    @Test
    public void testBundleWithWarnings()
	    throws JsonParseException, JsonMappingException, IOException, InputLimitException, DistributionException {

	DefaultDataPackagingService ddpkService;
	FileRequest[] rUrls = new FileRequest[2];
	long maxFSize = 1000000;
	int nOfFiles = 100;
	String domains = "nist.gov|s3.amazonaws.com/nist-midas";
	BundleRequest bRequest;
	rUrls = new FileRequest[2];
	String file1 = "{\"filePath\":\"/1894/license.pdf\",\"downloadUrl\":\"https://s3.amazonaws.com/nist-midas/1894/license.pdf\"}";
	String file2 = "{\"filePath\":\"/1894/license2.pdf\",\"downloadUrl\":\"https://test.testnew.com/nist-midas/1894/license.pdf\"}";

	ObjectMapper mapper = new ObjectMapper();
	FileRequest fileReq1 = mapper.readValue(file1, FileRequest.class);
	FileRequest fileReq2 = mapper.readValue(file2, FileRequest.class);
	rUrls[0] = fileReq1;
	rUrls[1] = fileReq2;
	bRequest = new BundleRequest("testdatabundle", rUrls);
	ddpkService = new DefaultDataPackagingService(domains, maxFSize, nOfFiles);
	Path path = Files.createTempFile("testdatabundle", ".zip");
	OutputStream os = Files.newOutputStream(path);
	ZipOutputStream zos = new ZipOutputStream(os);
	ddpkService.getBundledZipPackage(bRequest, zos);
	zos.close();

	try (ZipFile file = new ZipFile(path.toString())) {
	    FileSystem fileSystem = FileSystems.getDefault();
	    // Get file entries
	    Enumeration<? extends ZipEntry> entries = file.entries();
	    ZipEntry entry1 = entries.nextElement();
	    // Just check first entry in zip
	    assertEquals(entry1.getName(), "/1894/license.pdf");

	    // Iterate over entries
	    while (entries.hasMoreElements()) {
		ZipEntry entry = entries.nextElement();

		assertEquals(entry.getName(), "BundleInfo.txt");
		InputStream stream = file.getInputStream(entry);

		String expectedStr = "Url here:https://test.testnew.com/nist-midas/1894/license.pdf does not belong to allowed domains, so no file is downnloaded for this";
		String str;
		int count = 0;
		try {
		    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		    if (stream != null) {
			count++;
			while ((str = reader.readLine()) != null) {
			    // System.out.println("line no:"+count+"::::"+str);
			    if (count == 4)
				assertEquals(str, expectedStr);
			}
		    }
		} finally {
		    try {
			stream.close();
		    } catch (Throwable ignore) {
		    }
		}

	    }
	} catch (IOException ixp) {

	}

    }
}
