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
package gov.nist.oar.clients;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * an interface for resolving Resource identifiers to their associated NERDm metadata via a service.
 */
public interface ResourceResolver {

    /**
     * return a NERDm resource metadata record corresponding to the given PDR identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveResourceID(String id) throws OARServiceException;

    /**
     * return a NERDm resource metadata record corresponding to the given (NIST) EDI identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveEDIID(String ediid) throws OARServiceException;

    /**
     * return a NERDm component metadata record corresponding to the given PDR Component identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveComponentID(String id) throws OARServiceException;

    /**
     * return a NERDm metadata record corresponding to the given ID.  The implementation should attempt
     * to recognize the type of identifier provided and return the appropriate corresponding metadata.
     * If no record exists with the identifier, null is returned.
     * @throws AmbiguousIDException  if the identifier cannot be resolved because its type is ambiguous
     * @throws OARServiceException   if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolve(String id) throws OARServiceException;
}


    
