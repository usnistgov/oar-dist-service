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
 * 
 * @author: Raymond Plante
 */
package gov.nist.oar.distrib.cachemgr.pdr;

/**
 * an object for collecting options used for caching data.
 */
public class CacheOpts {
    public int prefs = 0;
    public String seq = null;
    public boolean recache = false;

    public CacheOpts() { }

    public CacheOpts(boolean recache, int prefs, String seq) {
        this.prefs = prefs;
        this.recache = recache;
        this.seq = seq;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder("re=");
        sb.append((recache) ? "1" : "0");
        if (seq != null) {
            if (sb.length() > 0) sb.append(",");
            sb.append("seq=").append(seq);
        }
        if (prefs != 0) {
            if (sb.length() > 0) sb.append(",");
            sb.append("pr=").append(Integer.toString(prefs));
        }
        return sb.toString();
    }

    public static CacheOpts parse(String optstr) {
        CacheOpts out = new CacheOpts();
        if (optstr == null || optstr.equals("0"))  // legacy syntax
            return out;
        if (optstr.equals("1")) {                   // legacy syntax
            out.recache = true;
            return out;
        }
        
        String[] opts = optstr.split(",\\s*");
        for (String opt : opts) {
            String[] kv = opt.split("=", 2);
            if (kv.length > 1) {
                kv[0] = kv[0].toLowerCase();
                if ("recache".startsWith(kv[0]))
                    out.recache = kv[1].equals("1");
                else if ("prefs".startsWith(kv[0])) {
                    try { out.prefs = Integer.parseInt(kv[1]); }
                    catch (NumberFormatException ex) { }
                }
                else if ("sequence".startsWith(kv[0]))
                    out.seq = kv[1];
            }       
        }

        return out;
    }
}
