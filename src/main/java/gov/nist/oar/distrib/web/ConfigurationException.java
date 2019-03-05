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
 */
package gov.nist.oar.distrib.web;

/**
 * an exception indicating an error while assembling and configuring an application.  When this
 * exception is caught by the <a href="https://spring.io/" target="_top">spring boot framework</a>,
 * execution ceases.
 */
public class ConfigurationException extends Exception {

    protected String parameter = null;
    protected String reason = null;

    /**
     * Create an exception with an arbitrary message
     */
    public ConfigurationException(String msg) { super(msg); }

    /**
     * Create an exception about a specific parameter.  The parameter will be combined with 
     * the given reason.
     * 
     * @param param   the configuration parameter name whose value (or lack thereof)
     *                has resulted in an error.
     * @param reason  an explanation of what is wrong with the parameter.  This will be combined
     *                with the parameter name to created the exception message (returned via
     *                {@code getMessage()}.
     * @param cause   An underlying exception that was thrown as a result of the parameter value.
     */
    public ConfigurationException(String param, String reason) {
        this(param, reason, null);
    }

    /**
     * Create an exception about a specific parameter.  The parameter will be combined with 
     * the given reason.
     * 
     * @param param   the configuration parameter name whose value (or lack thereof)
     *                has resulted in an error.
     * @param reason  an explanation of what is wrong with the parameter.  This will be combined
     *                with the parameter name to created the exception message (returned via
     *                {@code getMessage()}.
     * @param cause   An underlying exception that was thrown as a result of the parameter value.
     */
    public ConfigurationException(String param, String reason, Throwable cause) {
        super(param + ": " + reason, cause);
        parameter = param;
        this.reason = reason;
    }

    /**
     * return the name of the parameter that was incorrectly set
     */
    public String getParameterName() {  return parameter;  }

    /**
     * return the explanation of how parameter is incorrect.  This will not include the 
     * parameter name.  
     * 
     * {@see #getParamterName}
     * {@see #getMessage}
     */
    public String getReason() {  return reason;  }
}
