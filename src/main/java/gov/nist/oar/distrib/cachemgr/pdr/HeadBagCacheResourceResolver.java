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
package gov.nist.oar.distrib.cachemgr.pdr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import gov.nist.oar.clients.AmbiguousIDException;
import gov.nist.oar.clients.OARServiceException;
import gov.nist.oar.clients.ResourceResolver;
import gov.nist.oar.distrib.cachemgr.CacheManager;

/**
 * A client for accessing resource metadata from an archive of "head-bag" preserservation bag files.
 * <p>
 * The NIST Public Data Repository (PDR) preserves its data into a preservation format that consists of 
 * aggregations of files conforming the to the BagIt standard using the NIST PDR BagIt profile.  The 
 * profile itself is an extenstion of the more general Multibag Profile.  This latter profile defines the 
 * concept of a head bag that provides a directory for all data in the aggregation; in the PDR extension
 * profile, the complete metadata is also stored in the head bag.  In the PDR, preservation bag files are 
 * stored in an AWS S3 bucket.  
 * <p>
 * This implementation provides a front-end to a cache of head bags that can reside on local disk for faster,
 * more robust access.  The cache is handled by a CacheManager that can restore head bags from the long term
 * storage (i.e. the AWS S3 bucket) as needed, while also ensuring protection from corruption.  
 */
public class HeadBagCacheResourceResolver implements ResourceResolver, PDRConstants {

    CacheManager hbmgr = null;
    // HeadBagDB hbdb = null;
    final String naan;
    final Pattern ARK_ID_PAT;

    /**
     * wrap a {@link gov.nist.oar.clients.ResourceResolver} interface around a 
     * {@link gov.nist.oar.distrib.cachemgr.CacheManager} that manages local copies of head bags.
     * @param headbagcache    the {@link gov.nist.oar.distrib.cachemgr.CacheManager} that manages local 
     *                        copies of head bags.
     * @param arknaan         the NAAN number to assume for ARK identifiers
     */
    public HeadBagCacheResourceResolver(CacheManager headbagcache, /* HeadBagDB db, */ String arknaan) {
        hbmgr = headbagcache;
        // hbdb = db;
        naan = arknaan;
        ARK_ID_PAT = Pattern.compile("^ark:/"+naan+"/");
    }

    /**
     * wrap a {@link gov.nist.oar.clients.ResourceResolver} interface around a 
     * {@link gov.nist.oar.distrib.cachemgr.CacheManager} that manages local copies of head bags.
     */
    public HeadBagCacheResourceResolver(CacheManager headbagcache /*, HeadBagDB db */) {
        this(headbagcache, /* db, */ NIST_ARK_NAAN);
    }

    /**
     * return a NERDm resource metadata record corresponding to the given (NIST) EDI identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveEDIID(String ediid) throws OARServiceException {
        // String aipid = ediid;
        Matcher mat = ARK_ID_PAT.matcher(ediid);
        if (mat.matches())
            mat.replaceFirst("");

        // List<CacheObject> hbs = hbdb.findHeadBag(aipid);
        return null;
    }

    /**
     * return a NERDm resource metadata record corresponding to the given PDR identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveResourceID(String id) throws OARServiceException {
        return null;
    }

    /**
     * return a NERDm component metadata record corresponding to the given PDR Component identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveComponentID(String id) throws OARServiceException {
        return null;
    }

    /**
     * return a NERDm metadata record corresponding to the given ID.  The implementation should attempt
     * to recognize the type of identifier provided and return the appropriate corresponding metadata.
     * If no record exists with the identifier, null is returned.
     * @throws AmbiguousIDException  if the identifier cannot be resolved because its type is ambiguous
     * @throws OARServiceException   if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolve(String id) throws OARServiceException {
        return null;
    }

    

}
