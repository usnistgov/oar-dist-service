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
package gov.nist.oar.distrib.web;

import java.io.FileNotFoundException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StreamHandle;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.ResourceNotFoundException;
import gov.nist.oar.distrib.service.PreservationBagService;

import gov.nist.oar.ds.exception.IDNotFoundException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;



@RestController
@Api(value = "To get data packages and information.", tags = "PreservationBag Service API")
/**
 * A controller that provides the web service API to the 
 * {@link gov.nist.oar.distrib.service.PreservationBagService PreservationBagService}
 *
 * @author Deoyani Nandrekar-Heinis
 */
@RequestMapping(value = "/presbag")
public class PreservationBagController {
  
    Logger logger = LoggerFactory.getLogger(PreservationBagController.class);

    public static final String CONTENT = "Welcome to the OAR preservation service api";
  
    @Autowired
    PreservationBagService pres;

    /**
     * Returns checksum value for given bag by reading contents of .sha256 file
     * associated with that bag.
     * @param enter bagfilenname to get the checksum of that file. /checksum/{filename} 
     * @return String the checksum value
     * @throws FileNotFoundException
     */
    @ApiOperation(value = "Returns checksum for given filename.",nickname = "Get Checksum",notes = "PreservationBag Controller.")
    @RequestMapping(value = "/checksum/{filename}", method = RequestMethod.GET)
    public ResponseEntity<String> getChecksum(HttpServletRequest request)
        throws FileNotFoundException, DistributionException
    {
        String restOfTheUrl = (String) request.getAttribute(
                                                            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        logger.info("param::"+restOfTheUrl);
        restOfTheUrl = restOfTheUrl.replace("/checksum/", "");
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);

        Checksum test = pres.getInfo(restOfTheUrl).checksum;
  
        return new ResponseEntity<>(test.hash,httpHeaders, HttpStatus.OK);
    }
  
    /**
     * Get the Headbag for given record in the long term storage.
     * @param psId preservation id or Record id
     * @return String Head bag name
     * @throws DistributionException
     */
    @ApiOperation(value = "Returns Headbag for given identifier/record id.",nickname = "Get Headbag",notes = "PreservationBag Controller.")
    @RequestMapping(value = "/{psId}/headBag", method = RequestMethod.GET)
    public ResponseEntity<String> getHeadBag(@PathVariable("psId") String psId) throws DistributionException{ 
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
        return  new ResponseEntity<>( pres.getHeadBagName(psId),httpHeaders,HttpStatus.OK);
    }
  
    /**
     * Returns list of the bags associated with given record
     * @param psId preservation bag id or record id
     * @return List of bags available for given id
     * @throws DistributionException 
     */
    @ApiOperation(value = "Returns List of Bags.",nickname = "Get ListOfBags",notes = "PreservationBag Controller.") 
    @RequestMapping(value = "/{psId}/listbags", method = RequestMethod.GET)
    public ResponseEntity<List<String>> getListBags(@PathVariable("psId") String psId) throws DistributionException{
        HttpHeaders httpHeaders = new HttpHeaders();
    
        return new ResponseEntity<>(pres.listBags(psId),httpHeaders,HttpStatus.OK);
    }
  
    /**
     * Downloads the bag for given bagname.
     * @param request
     * @return File Stream 
     * @throws FileNotFoundException
     */
    @ApiOperation(value = "Dowloads given bag file.",nickname = "Get Info",notes = "PreservationBag Controller.")
    @RequestMapping(value = "/download/{bagfile}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getInfo(HttpServletRequest request)
        throws FileNotFoundException, DistributionException
    {
        String restOfTheUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        logger.info("param::"+restOfTheUrl);
        restOfTheUrl = restOfTheUrl.replace("/download/", "");
    
        StreamHandle stHandle = pres.getBag(restOfTheUrl);
    
        InputStreamResource inputStreamResource = new InputStreamResource(stHandle.dataStream);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.setContentLength(stHandle.getInfo().contentLength);
        httpHeaders.setContentDispositionFormData("attachment",  restOfTheUrl);

        return  new ResponseEntity<>(inputStreamResource,httpHeaders,HttpStatus.OK);
    }
  
  
}
