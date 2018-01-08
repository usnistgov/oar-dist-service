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
 * InternalServerException exception
 * @author Deoyani Nandrekar-Heinis
 *
 */
@ResponseStatus(value=HttpStatus.INTERNAL_SERVER_ERROR, reason="Internal Server Error") //500
public class InternalServerException  extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String requestUrl = "";
	/***
	 * InternalServerException for given id
	 * @param id requested identifier
	 */
	public InternalServerException(int id){
		super("The record with given Id="+id);
	}
	/**
	 * InternalServerException
	 */
	public InternalServerException(){
		super("There is an error running your query on the server.");
	}
	/***
	 * KeyWordNotFoundException for requested URL
	 * @param requestUrl
	 */
	public InternalServerException(String requestUrl){
		
		super("Internal Server Error for given URL."+requestUrl );
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
