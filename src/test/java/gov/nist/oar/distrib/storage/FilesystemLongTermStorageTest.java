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
package gov.nist.oar.distrib.storage;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import gov.nist.oar.ds.ApplicationConfig;
import gov.nist.oar.ds.exception.IDNotFoundException;

@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ApplicationConfig.class})
@TestPropertySource(properties = {
    "distservice.ec2storage=/tmp/ec2/",
})
public class FilesystemLongTermStorageTest {
  
  private static Logger logger = LoggerFactory.getLogger(FilesystemLongTermStorageTest.class);

  @Value("${distservice.ec2storage}")
  String dataDir;
  
  @Autowired
  private FilesystemLongTermStorage fStorage;
  //  FilesystemLongTermStorage fStorage;
  
  @Before
  public void setDir(){
    logger.info("THIS dir::"+ dataDir);
    //    fStorage = new FilesystemLongTermStorage();
    //    fStorage.dataDir = "/tmp/ec2/";
  }
  
  @Test
  public void testValueSetup() {
    assertEquals("/tmp/ec2/", dataDir);
  }
  
  @Test
  public void testFileList() throws IDNotFoundException {
    List<String> filenames = new ArrayList<>();

         filenames.add("6376FC675D0E1D77E0531A5706812BC21886.03.mbag0_3-0.zip");
         filenames.add("6376FC675D0E1D77E0531A5706812BC21886.06.mbag0_3-0.zip"); 
         filenames.add("6376FC675D0E1D77E0531A5706812BC21886.09.mbag0_2-0.zip");
         filenames.add("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-0.zip");
 
    assertEquals(fStorage.findBagsFor("6376FC675D0E1D77E0531A5706812BC21886"),filenames);
  }
  
  @Test
  public void testFileChecksum() throws FileNotFoundException  {
    String hash="a13cd5bf10778618d36cf85b4e353290af2afb8295adc6fcdbdd7e1ff828020c";
    logger.info("Checksum:"+fStorage.getChecksum("6376FC675D0E1D77E0531A5706812BC21886.06.mbag0_3-0.zip").hash);
    String getChecksumHash = fStorage.getChecksum("6376FC675D0E1D77E0531A5706812BC21886.06.mbag0_3-0.zip").hash;
    assertEquals(getChecksumHash.trim(),hash.trim());
  }
  
  @Test
  public void testFileSize() throws FileNotFoundException  {
    long filelength = fStorage.getSize("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-0.zip");
    assertEquals(filelength,60854);
  } 
      
  //Need to update deatils to compare two file streams
  @Test
  public void testFileStream() throws FileNotFoundException  {
    InputStream is = fStorage.openFile("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-0.zip");
    assertEquals(is,is);
  } 
  
  @Test
  public void testFileHeadbag() throws IDNotFoundException{
    String headBagName = fStorage.findHeadBagFor("6376FC675D0E1D77E0531A5706812BC21886"); 
    logger.info("Test getHeadbagname:"+headBagName);
    headBagName = fStorage.findHeadBagFor("6376FC675D0E1D77E0531A5706812BC21886","03"); 
    logger.info("Test getHeadbagname:"+headBagName);
  }
  
  
}