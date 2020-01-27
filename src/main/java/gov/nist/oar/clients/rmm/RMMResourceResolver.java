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
package gov.nist.oar.distrib.clients.rmm;

import gov.nist.oar.distrib.clients.ResourceResolver;
import gov.nist.oar.distrib.clients.OARServiceException;
import gov.nist.oar.distrib.clients.OARWebServiceException;
import gov.nist.oar.distrib.clients.AmbiguousIDException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

import java.util.Collection;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.HttpURLConnection;


/**
 * A client for accessing the Resource Metadata Manager (RMM) that focuses on resolving resource identifiers
 * to their associated NERDm metadata.  
 * 
 * This implementation incorporates a global internal cache for caching information about components.  The 
 * cache is unique to the RMM service endpoint.  
 */
public class RMMResourceResolver implements ResourceResolver {

    /**
     * the global caches for Component metadata, one for each endpoint.  (There is typically only one 
     * endpoint per application.)
     */
    public static Map<String, ComponentInfoCache> compCaches = new HashMap<String, ComponentInfoCache>(2);

    protected String epURL = null;
    ComponentInfoCache compcache = null;

    RMMResourceResolver(String endpoint) {
        try {
            URI url = (new URI(endpoint)).normalize();
            if (! url.getScheme().equals("http") && ! url.getScheme().equals("https"))
                throw new IllegalArgumentException(endpoint + ": Not an HTTP URL");
            epURL = url.toString();
            while (epURL.endsWith("/"))
                epURL = epURL.substring(0, epURL.length()-1);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(endpoint + ": Not a legal URL: " + ex.getMessage(), ex);
        }
    }

    /**
     * create the resolver.  A global cache (unique to the given endpoint URL) will be used to cache
     * Component metadata.  If a cache does not already exist one will be created with the given 
     * constraints.  
     * @param endpoint             the endpoint of the RMM service
     * @param cacheSizeLimit       the limit on the number of records in the cache (ignored if there is 
     *                               already a global cache available).
     * @param cacheExpireTimeSecs  how long records should remain in the cache, in seconds (ignored if 
     *                               there is already a global cache available).
     */
    public RMMResourceResolver(String endpoint, int cacheSizeLimit, int cacheExpireTimeSecs) {
        this(endpoint);
        compcache = ensureCache(epURL, cacheSizeLimit, cacheExpireTimeSecs);
    }

    /**
     * create the resolver.  The given cache object will be used to cache component descriptions 
     * @param endpoint             the endpoint of the RMM service
     * @param cache                the cache to use to cache component descriptions.
     */
    public RMMResourceResolver(String endpoint, ComponentInfoCache cache) {
        this(endpoint);
        compcache = cache;
    }

    protected ComponentInfoCache ensureCache(String epkey, int cacheSizeLimit, int cacheExpireTimeSecs) {
        synchronized (compCaches) {
            ComponentInfoCache out = compCaches.get(epkey);
            if (out == null) {
                out = new ComponentInfoCache(cacheSizeLimit, cacheExpireTimeSecs,
                                             Arrays.asList("nrdp:DataFile", "nrdp:DownloadableFile"),
                                             Arrays.asList("nrd:Hidden"), 10);
                compCaches.put(epkey, out);
            }
            return out;
        }
    }

    /**
     * submit a GET on a given URL and return its expected JSON Object response or null if 
     * the URL returns 404 (Not Found).
     * @throws OARWebServiceException   if the service responds with an error &gt;= 300.
     * @throws JSONException            if the message is not parseable as JSON
     * @throws IOException              if an occurs specifically while reading the input stream
     */
    protected JSONObject getJSON(URL res)
        throws OARWebServiceException, JSONException, IOException
    {
	HttpURLConnection conn = null;
        InputStream body = null;
        try {
            conn = (HttpURLConnection) res.openConnection();

	    conn.setInstanceFollowRedirects(true);
	    conn.setConnectTimeout(10000);
	    conn.setReadTimeout(300000);
	    conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

	    int code = conn.getResponseCode();
	    long length = conn.getContentLengthLong();

            if (code == 404)
                return null;
            
            if (code >= 300 || code < 200)
                throw new OARWebServiceException(code, conn.getResponseMessage());

            body = conn.getInputStream();
            return new JSONObject(new JSONTokener(body));
        }
        finally {
            if (body != null) drain(body);
        }
    }

    /*
     * read any remaining data from stream and close it.
     */
    private final void drain(InputStream is) {
        byte[] buf = new byte[4096];
        int n = 0;
        try {
            while ((n = is.read(buf)) >= 0) { /* drop on the floor */ }
        } catch (IOException ex) { /* ignore */ }
        try {
            is.close();
        } catch (IOException ex) { /* at least we tried */ }
    }
        

    /**
     * return a NERDm resource metadata record corresponding to the given PDR identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveResourceID(String id) throws OARServiceException {
        String ep = epURL + "?@id=" + id;
        try {
            URL url = new URL(ep);
            JSONObject out = toResource(getJSON(url));
            if (out != null)
                compcache.cacheResource(out, true, null);
            return out;
        }
        catch (MalformedURLException ex) {  // should not happen
            throw new OARServiceException("URL build failure on "+ep+": "+ex.getMessage(), ex);
        }
        catch (IOException ex) {
            throw new OARServiceException("Failure reading remote stream: "+ex.getMessage(), ex);
        }
        catch (JSONException ex) {
            throw new OARServiceException("Failed to parse record: "+ex.getMessage(), ex);
        }
    }

    /**
     * if found, strip out the RMM search response envelope and annotations
     */
    JSONObject toResource(JSONObject resp) throws JSONException {
        if (resp == null)
            return null;
        if (resp.has("ResultData")) {
            JSONArray res = resp.getJSONArray("ResultData");
            if (res.length() == 0)
                return null;
            resp = res.getJSONObject(0);
            resp.remove("_id");
        }
        return resp;
    }

    /**
     * return a NERDm resource metadata record corresponding to the given (NIST) EDI identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveEDIID(String ediid) throws OARServiceException {
        String ep = epURL + "/" + ediid;
        try {
            URL url = new URL(ep);
            JSONObject out = toResource(getJSON(url));
            if (out != null)
                compcache.cacheResource(out, true, null);
            return out;
        }
        catch (MalformedURLException ex) {  // should not happen
            throw new OARServiceException("URL build failure on "+ep+": "+ex.getMessage(), ex);
        }
        catch (IOException ex) {
            throw new OARServiceException("Failure reading remote stream: "+ex.getMessage(), ex);
        }
        catch (JSONException ex) {
            throw new OARServiceException("Failed to parse record: "+ex.getMessage(), ex);
        }
    }

    /**
     * return a NERDm component metadata record corresponding to the given PDR Component identifier, or 
     * null if no record exists with this identifier.
     * @throws OARServiceException  if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolveComponentID(String id) throws OARServiceException {
        JSONObject out = compcache.get(id, true);
        if (out != null)
            return out;
        
        String[] rescmp = splitID(id);
        if (rescmp[1] == null || rescmp[1].length() == 0)
            throw new OARServiceException("Not recognized as a Component identifier: "+ id);
        
        return resolveComponentID(rescmp[0], rescmp[1]);
    }
    
    JSONObject resolveComponentID(String resid, String compid) throws OARServiceException {
        JSONObject res = null;
        if (isRecognizedEDIID(resid)) 
            res = resolveEDIID(resid);
        else
            res = resolveResourceID(resid);
        
        if (res == null)
            return null;
        return compcache.cacheResource(res, false, compid);
    }

    private final String joinID(String resid, String compid) {
        if (compid.startsWith("#") || compid.startsWith("/"))
            return resid + compid;
        return resid + "/" + compid;
    }

    /**
     * return a NERDm metadata record corresponding to the given ID.  The implementation should attempt
     * to recognize the type of identifier provided and return the appropriate corresponding metadata.
     * If no record exists with the identifier, null is returned.
     * @throws AmbiguousIDException  if the identifier cannot be resolved because its type is ambiguous
     * @throws OARServiceException   if something goes wrong with the interaction with resolving service.
     */
    public JSONObject resolve(String id) throws OARServiceException {
        String[] rescmp = splitID(id);

        if (rescmp[1] != null && rescmp[1].length() != 0) {
            JSONObject out = compcache.get(id, true);
            if (out != null)
                return out;
            return resolveComponentID(rescmp[0], rescmp[1]);
        }

        if (isRecognizedEDIID(rescmp[0]))
            return resolveEDIID(rescmp[0]);

        return resolveResourceID(rescmp[0]);
    }

    private final Pattern NIST_ARK_EDIID_PAT = Pattern.compile("^ark:/88434/mds\\d+\\-");

    /**
     * return true if the given identifier is recognized as an EDI identifier.
     * This implementation makes assumptions about the form of NIST Enterprise Data Inventory 
     * identifiers.  
     */
    public boolean isRecognizedEDIID(String id) throws AmbiguousIDException {
        if (NIST_ARK_EDIID_PAT.matcher(id).find())
            return true;
        else if (! id.startsWith("ark:") && id.length() > 30)
            return true;
        return false;
    }

    private final Pattern RES_COMP_DELIM = Pattern.compile("[/#]");
    private final Pattern NIST_ARK_PDR_PAT = Pattern.compile("^ark:/\\d+/");

    /**
     * attept to split the given identifier into a resource identifier and a component sub-identifier.
     * This makes assumptions about the form of NIST PDR identifiers
     */
    public String[] splitID(String id) throws AmbiguousIDException {
        String prefix = "";
        Matcher m = NIST_ARK_PDR_PAT.matcher(id);
        if (m.find()) {
            prefix = id.substring(0, m.end());
            id = id.substring(m.end());
        }
        else {
            // try parsing it as a URI
            try {
                URI asuri = new URI(id);
                if (asuri.getAuthority() != null)
                    throw new AmbiguousIDException(id);
            } catch (URISyntaxException ex) { }
        }

        String[] out = new String[2];
        m = RES_COMP_DELIM.matcher(id);
        if (m.find()) {
            out[0] = prefix + id.substring(0, m.start());
            out[1] = (m.group().equals("#")) ? "#"+id.substring(m.end()) : id.substring(m.end());
        }
        else
            out[0] = prefix + id;

        return out;
    }

    /**
     * return the current number of component records cached in the internal component cache
     */
    public int getCacheSize() { return compcache.size(); }

    /**
     * return the current capacity of the component cache
     */
    public int getCacheCapacity() { return compcache.getCapacity(); }

    /**
     * change the capacity of the internal component cache
     */
    public void setCacheCapacity(int n) { compcache.setCapacity(n); }
}
