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
package gov.nist.oar.unit.service.impl;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.service.impl.PreservationBagServiceImpl;
import gov.nist.oar.ds.ApplicationConfig;

import gov.nist.oar.ds.exception.IDNotFoundException;
import gov.nist.oar.ds.exception.ResourceNotFoundException;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ApplicationConfig.class})
public class PreservationServiceTest {
  private static Logger logger = LoggerFactory.getLogger(PreservationServiceTest.class);
  
  @Value("${distservice.ec2storage}")
  String filesystem;
  
  @Autowired
  PreservationBagService pres;
  
  List<String> filenames = new ArrayList<>();
  @Before
  public void setUp() {
    logger.info("Preservation TEST ::"+filesystem);
   
    filenames.add("6376FC675D0E1D77E0531A5706812BC21886.mbag0_2-0.zip");
    //filenames.add("6376FC675D0E1D77E0531A5706812BC21886.mbag0_2-0.zip.sha256");
    filenames.add("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-0.zip");
    //filenames.add("6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-0.zip.sha256");
      
  }

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Test
  public void listBagsException() throws IDNotFoundException{
   
    exception.expect(ResourceNotFoundException.class);
    List<String> fnames = this.pres.listBags("6376FC675D0E1D77E0531A5706812BC00001");
     
      logger.info("Size of list :"+ fnames.size());
//    assertEquals(filenames, pres.listBags("6376FC675D0E1D77E0531A5706812BC21886"));
//    assertEquals(0, pres.listBags("6376FC675D0E1D77E0531A5706812BC00001").size());      
  }
  
  @Test
  public void listBags() throws IDNotFoundException{
    List<String> fnames = this.pres.listBags("6376FC675D0E1D77E0531A5706812BC21886");
    logger.info(" Size of Bags list" + fnames.size());
    assertEquals(filenames,fnames);
    assertEquals(filenames.size(),2);
  }
  
  @Test
  public void getHeadBagName() throws IDNotFoundException{
    String headbag = "6376FC675D0E1D77E0531A5706812BC21886.mbag0_3-0.zip";
    assertEquals(this.pres.getHeadBagName("6376FC675D0E1D77E0531A5706812BC21886"), headbag);
  }
  
  
  
//  @Test
//  public void getHeadBagNameWithVersion() throws IDNotFoundException{
//    
//    
//  }
}
