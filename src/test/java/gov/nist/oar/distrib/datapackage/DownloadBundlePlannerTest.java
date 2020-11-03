package gov.nist.oar.distrib.datapackage;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.oar.RequireWebSite;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.web.InvalidInputException;

public class DownloadBundlePlannerTest {

	@ClassRule
	public static TestRule siterule = new RequireWebSite("https://s3.amazonaws.com/nist-midas/1894/license.pdf");

	private static long mxFileSize = 1000000;
	private static int numberofFiles = 100;
	private static String domains = "nist.gov|s3.amazonaws.com/nist-midas|httpstat.us";
	private static int redirectURLTrials = 1;
	private static FileRequest[] inputfileList = new FileRequest[2];
	private static BundleRequest bundleRequest;
	protected static Logger logger = LoggerFactory.getLogger(DefaultDataPackagerTest.class);
	private static DownloadBundlePlanner dplanner;
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
		dplanner = new DownloadBundlePlanner(bundleRequest, mxFileSize, numberofFiles, domains, "bundlename",
				redirectURLTrials,"");
	}

	private static void createBundleRequest() throws IOException {
		inputfileList = new FileRequest[2];
		ObjectMapper mapper = new ObjectMapper();
		FileRequest testval1 = mapper.readValue(val1, FileRequest.class);
		FileRequest testval2 = mapper.readValue(val2, FileRequest.class);
		inputfileList[0] = testval1;
		inputfileList[1] = testval2;
		bundleRequest = new BundleRequest("testdatabundle", inputfileList, 0,2);
	}
	
	 @Test
	    public void testGetBundleDownloadPlan() throws MalformedURLException, IOException, DistributionException, InvalidInputException {
		 BundleDownloadPlan plan = dplanner.getBundleDownloadPlan();
		 assertEquals(plan.getFilesCount(), 2);
		 assertEquals(plan.getBundleCount(), 1);
		 assertEquals(plan.getSize(),62562);
		 assertEquals(plan.getStatus(),"complete");
	    }

}
