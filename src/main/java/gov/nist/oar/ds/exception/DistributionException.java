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

/**
 * @author Deoyani Nandrekar-Heinis
 * Application specific exception
 */
public class DistributionException extends Exception{
	
	private static final long serialVersionUID = 1L;
	
	public DistributionException(int id){
		super("Exception Thrown for this record="+id);
	}
	public DistributionException(String requestUrl){
		super("Exception Thrown for this request="+requestUrl);
	}
	
	public DistributionException(String requestUrl, String message){
		super("Exception Thrown for this request="+requestUrl+ " Exception Message:"+message);
	}
	public DistributionException(){
		super("Resource you are looking for is not available.");
	}

}
