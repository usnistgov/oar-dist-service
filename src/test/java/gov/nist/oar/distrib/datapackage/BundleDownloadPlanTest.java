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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Deoyani Nandrekar-Heinis This is the test class for java object
 *         representing bundle plan json.
 */
public class BundleDownloadPlanTest {

    public BundleRequest[] makeBundles() {
	FileRequest fpathUrl_1 = new FileRequest("/filepath/file-1.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	FileRequest fpathUrl_2 = new FileRequest("/filepath/file-2.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	BundleRequest bundle1 = new BundleRequest("download_data_1",
		new FileRequest[] { fpathUrl_1, fpathUrl_2 });

	FileRequest fpathUrl_3 = new FileRequest("/filepath-2/testfile-1.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	FileRequest fpathUrl_4 = new FileRequest("/filepath-2/testfile-2.pdf",
		"https://s3.amazonaws.com/nist-midas/1894/license.pdf");
	BundleRequest bundle2 = new BundleRequest("download_data_2",
		new FileRequest[] { fpathUrl_3, fpathUrl_4 });

	return new BundleRequest[] { bundle1, bundle2 };
    }

    public String[] makeMessages() {
	return new String[] { "Some files are not included.", "Domains are not allowed." };
    }

    public NotIncludedFile[] makeNotIncluded() {
	NotIncludedFile notIn = new NotIncludedFile("/testPath/testFile",
		"https://s3.amazonaws.com/nist-midas-org/1894/license.pdf", "Not allowed domain.");
	NotIncludedFile notIn2 = new NotIncludedFile("/testPath/testFile2",
		"https://s3.amazonaws.com/nist-midas-org/1894/license2.pdf", "Not allowed domain.");

	return new NotIncludedFile[] { notIn, notIn2 };
    }

    @Test
    public void testBundleDownloadPlan() {
	BundleDownloadPlan bundlePlan = new BundleDownloadPlan("_bundle", "partial", this.makeBundles(),
		this.makeMessages(), this.makeNotIncluded(),0,0,0);

	assertEquals("_bundle", bundlePlan.getPostEachTo());
	assertEquals("partial", bundlePlan.getStatus());
	assertEquals("download_data_1", bundlePlan.getBundleNameFilePathUrl()[0].getBundleName());
	assertEquals("/testPath/testFile2", bundlePlan.getNotIncluded()[1].getFilePath());
    }
}
