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
package gov.nist.oar.ds.util;
/***
 * ErrorInfo Class gives a Structure to return Error
 * @author dsn1
 *
 */
public class ErrorInfo {
//    public final String url;
//    public final String ex;
//    public final String message;
//    public final String httpStatus;
//
//    /***
//     * Get ErrorInfo
//     * @param url reuesturl
//     * @param ex exception thrown
//     * @param message Error message
//     * @param httpStatus http error code
//     */
//    public ErrorInfo(String url, Exception ex, String message, String httpStatus) {
//        this.url = url;
//        this.ex = ex.getMessage();
//        this.message = message;
//        this.httpStatus = httpStatus;
//    }
	
	 public final String url;
	   
	    public final String message;
	    public final String httpStatus;

	    /***
	     * Get ErrorInfo
	     * @param url reuesturl
	     * @param ex exception thrown
	     * @param message Error message
	     * @param httpStatus http error code
	     */
	    public ErrorInfo(String url, String message, String httpStatus) {
	        this.url = url;
	        
	        this.message = message;
	        this.httpStatus = httpStatus;
	    }
}