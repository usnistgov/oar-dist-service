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
 * 
 * @author:Harold Affo
 */
package gov.nist.oar.unit.service;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.zip.ZipInputStream;
import java.io.IOException;

import gov.nist.oar.ds.service.impl.DownloadServiceImpl;

public class DownloadServiceTest {

    @Test
    public void testLookupFileViaZip() throws IOException {
        String inbag = "67C783D4BA814C8EE05324570681708A1899.mbag0_3-1.zip";
        String fbag = "67C783D4BA814C8EE05324570681708A1899.mbag0_3-0.zip";
        ZipInputStream zis = new ZipInputStream(getClass().getResourceAsStream("/"+inbag));

        assertEquals(fbag, DownloadServiceImpl.lookupFileViaZip(zis, inbag,
                                                                "67C783D4BA814C8EE05324570681708A1899",
                                                                "NMRRVocab20171102.rdf"));

        inbag = "XXXX.mbag0_2-0.zip";
        zis = new ZipInputStream(getClass().getResourceAsStream("/"+inbag));
        assertEquals(inbag, DownloadServiceImpl.lookupFileViaZip(zis, inbag, "XXXX",
                                                                 "NMRRVocab20171102.rdf"));
        
    }
	
}



