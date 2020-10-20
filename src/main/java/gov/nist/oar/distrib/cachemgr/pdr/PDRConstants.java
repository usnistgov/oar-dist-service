/*
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.pdr;

import java.util.regex.Pattern;

/**
 * Constants related to the NIST Public Data Repository (PDR)
 */
public interface PDRConstants {

    /**
     * the pattern for PDR ARK-based identifiers.  This does not constrain the NAAN number.
     */
    public final Pattern PDR_ARK_PAT = Pattern.compile("^ark:/\\d+/");
    
    /**
     * The delimiter pattern used in PDR primary identifiers (as opposed to EDI-IDs or AIP IDs) that
     * separates the resource identifier part and the component part.  A slash ("/") is used when the 
     * component is a downloadable file, and a pound ("#") is used for other components.
     */
    public final Pattern RES_COMP_DELIM = Pattern.compile("[/#]");

    /**
     * The ARK identifier NAAN assigned to NIST
     */
    public final String NIST_ARK_NAAN = "88434";
}
