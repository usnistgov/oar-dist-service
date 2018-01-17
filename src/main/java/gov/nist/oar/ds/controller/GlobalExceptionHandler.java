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
package gov.nist.oar.ds.controller;


import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonMappingException;

import ch.qos.logback.classic.Level;
import gov.nist.oar.ds.util.ErrorInfo;
import gov.nist.oar.ds.exception.KeyWordNotFoundException;
import gov.nist.oar.ds.exception.ResourceNotFoundException;
import gov.nist.oar.ds.exception.DistributionException;
import gov.nist.oar.ds.exception.InternalServerException;

@ControllerAdvice
/***
 * GlobalExceptionHandler class takes care of any exceptions thrown in the code and 
 * returns appropriate messages
 * @author Deoyani Nandrekar-Heinis
 *
 */
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler  {
	
	private Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	@Autowired
    WebRequest request;
	@ResponseStatus(HttpStatus.CONFLICT)
 	  @ExceptionHandler(JsonMappingException.class)
	  @ResponseBody
	  /**
	   * Handles General Exception
	   * @param exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo  jsonErrors(JsonMappingException exception) {
	    logger.info("----Caught JsonMappingException----\n"+request.getDescription(false)+"\n Detail JsonMappingException:"+exception.getClass().getName());
	    logger.error(exception.getMessage(),exception);
	    return new ErrorInfo(request.getContextPath(), "There is some problem with JSON data.",HttpStatus.CONFLICT.toString());
      
	  }

 	 
	  @ResponseStatus(HttpStatus.CONFLICT)
	  @ExceptionHandler(Exception.class)
	  @ResponseBody
	  /**
	   * Handles General Exception
	   * @param exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo  myError(Exception exception) {
		  
	    logger.info("----Caught Exception----\n"+request.getDescription(false)+"\n Detail General Exception:"+exception.getMessage()+exception.getClass().getName());
	    logger.error(exception.getMessage(),exception);  
	    return new ErrorInfo(request.getContextPath(), "There is an error with this request, check request proeprly.",HttpStatus.CONFLICT.toString());
        
	  }
	  
	  @ResponseStatus(HttpStatus.BAD_REQUEST)
	  @ExceptionHandler(IllegalArgumentException.class)
	  @ResponseBody
	  /**
	   * Handles IllegalArgumentException
	   * @param IllegalArgumentException
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo illegal(IllegalArgumentException exception) {
            logger.info("----This is a illegal argument exception ----\n"+request.getDescription(false)+"\n Detail IllegalArgumentException:"+exception.getMessage()+exception.getClass().getName());
            logger.error(exception.getMessage(),exception);
            return new ErrorInfo(request.getContextPath(), "Requested parameters/arguments are not valid.",HttpStatus.BAD_REQUEST.toString());           
	  }
	  
	  @ResponseStatus(HttpStatus.NOT_FOUND)
	  @ExceptionHandler(KeyWordNotFoundException.class)
	  @ResponseBody
	  /***
	   * Handles KeywordNotFoundException
	   * @param exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo notFound(KeyWordNotFoundException exception) {
            logger.info("----Caught KeywordNotFoundException----\n"+request.getDescription(false)+"\n Detail NotFoundException:"+exception.getMessage()+exception.getClass().getName());
            logger.error(exception.getMessage(),exception);
            return new ErrorInfo(request.getContextPath(), "Requested Keyword is not valid.",HttpStatus.NOT_FOUND.toString());      
	  }
	  
	  
	  @ResponseStatus(HttpStatus.NOT_FOUND)
	  @ExceptionHandler(ResourceNotFoundException.class)
	  @ResponseBody
	  /***
	   * Handles ResourceNotFoundException
	   * @param ResourceNotFoundException
	   * @return ErrorInfo object with error details
	   */
	  public ResponseEntity<ErrorInfo> resourceNotFound(ResourceNotFoundException exception) {
            logger.info("----Caught ResourceNotFoundException----\n"+request.getDescription(false)+"\n Detail ResourceNotFoundException:"+exception.getMessage()+exception.getClass().getName());
            logger.error(exception.getMessage(),exception); 
            return new ResponseEntity<>(new ErrorInfo(request.getContextPath(), exception.getMessage(),HttpStatus.NOT_FOUND.toString()), HttpStatus.NOT_FOUND) ;
            
	  }
	  
	  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	  @ExceptionHandler(IOException.class)
	  @ResponseBody
	  /***
	   * Handles IOException
	   * @param Exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo ioError(IOException exception) {
            logger.info("----Caught IOException----\n"+request.getDescription(false)+"\n Detail IOException:"+exception.getMessage()+exception.getClass().getName());
            logger.error(exception.getMessage(),exception);
            return new ErrorInfo(request.getContextPath(), "Requested resources have some internal error in application.",HttpStatus.INTERNAL_SERVER_ERROR.toString());
	  }
	  
	  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	  @ExceptionHandler(InternalServerException.class)
	  @ResponseBody
	  /***
	   * Handles BadRequest
	   * @param Exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo internalError(InternalServerException exception) {
            logger.info("----Caught Internal Error Exception----\n"+request.getDescription(false)+"\n Detail InternalServerException:"+exception.getClass().getName());
            logger.error(exception.getMessage(),exception);
    	    return new ErrorInfo(request.getContextPath(), "Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR.toString());
	  }
	  
	  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	  @ExceptionHandler(RuntimeException.class)
	  @ResponseBody
	  /***
	   * Handles BadRequest
	   * @param Exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo runtimeError(RuntimeException exception) {
            logger.info("----Caught Runtime Exception----\n"+request.getDescription(false)+"\n Detail Runtime Exception:"+exception.getMessage()+exception.getStackTrace());
            logger.error(exception.getMessage(),exception);
    	    return new ErrorInfo(request.getContextPath(), "Runtime: Internal Server Error",HttpStatus.INTERNAL_SERVER_ERROR.toString());
	  }
	
	  @ResponseStatus(HttpStatus.NOT_FOUND)
	  @ExceptionHandler(FileNotFoundException.class)
	  @ResponseBody
	  /***
	   * Handles BadRequest
	   * @param Exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo fileNotFound(FileNotFoundException exception) {
            logger.info("----Caught Distribution Service Exception----\n"+request.getDescription(false)+"\n Details of Exception:"+exception.getMessage()+exception.getClass().getName());
            logger.error(exception.getMessage(),exception);
    	    return new ErrorInfo(request.getContextPath(), "Record/data Not found Error",HttpStatus.NOT_FOUND.toString());
      }
	  
	  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	  @ExceptionHandler(DistributionException.class)
	  @ResponseBody
	  /***
	   * Handles DistributionException
	   * @param Exception
	   * @return ErrorInfo object with error details
	   */
	  public ErrorInfo distService(DistributionException exception) {
            logger.info("----Caught Distribution Service Exception----\n"+request.getDescription(false)+"\n Details of Exception:"+exception.getClass().getName());
            logger.error(exception.getMessage(),exception);
    	    return new ErrorInfo(request.getContextPath(), "Distribution service has some Internal Error.",HttpStatus.INTERNAL_SERVER_ERROR.toString());
	  }
	  
	  
} 