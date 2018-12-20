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

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.InputLimitException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import springfox.documentation.annotations.ApiIgnore;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
@RestController
@Api
public class BundleDownloadPlanController {
	
	Logger logger = LoggerFactory.getLogger(BundleDownloadPlanController.class);

    @Value("${distrib.filesizelimit}")
    long maxfileSize;
  
    @Value("${distrib.numberoffiles}")
    int numofFiles;
    
    @Value("${distrib.validdomains}")
    String validdomains;
    
    @ApiOperation(value = "Get the plan to download given list of files. ", nickname = "get the plan to download data.",
            notes = "This api endpoint provides the information to client to how to divide request for number of files download "
            		+ "if some limits are not met.")
    @PostMapping(value = "/ds/_bundle_plan", consumes = "application/json", produces = "application/json")
    
    public void getbundlePlan( @Valid @RequestBody BundleNameFilePathUrl  jsonObject,@ApiIgnore HttpServletResponse response, Errors errors) 
		throws   DistributionException, InputLimitException{
    
    	
    }
    
}
