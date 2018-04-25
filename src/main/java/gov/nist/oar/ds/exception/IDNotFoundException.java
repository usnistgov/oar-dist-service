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
package gov.nist.oar.ds.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * KeyWordNotFound exception
 * @author Deoyani Nandrekar-Heinis
 *
 */
@ResponseStatus(value=HttpStatus.NOT_FOUND, reason="Resource Not Found") //404
public class IDNotFoundException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String requestUrl = "";
	/***
	 * KeyWordNotFoundException for given id
	 * @param id requested identifier
	 */
	public IDNotFoundException(int id){
		super("RecordNotFoundException with id="+id);
	}
	/**
	 * KeyWordNotFoundException
	 */
	public IDNotFoundException(){
		super("Keywords you  are looking for are not available.");
	}
	/***
	 * KeyWordNotFoundException for requested URL
	 * @param requestUrl
	 */
	public IDNotFoundException(String requestUrl){
		
		super("Keywords not available for given Request."+requestUrl );
		this.setRequestUrl(requestUrl);
	}
	
	/***
	 * Get RequestedURL
	 * @return String
	 */
	public String getRequestUrl(){
		return this.requestUrl;
	}
	
	/***
	 * Set RequestedURL
	 * @param url String
	 */
	public void setRequestUrl(String url){
		this.requestUrl = url;
	}
	
}
