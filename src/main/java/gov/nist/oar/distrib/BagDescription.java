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
package gov.nist.oar.distrib;

import gov.nist.oar.bags.preservation.BagUtils;

import java.io.File;
import java.util.List;
import java.text.ParseException;

/**
 * a file description with extra support for properties describing OAR preservation bags.
 * <p>
 * In the OAR PDR model, an <em>archive information package</em> (AIP) is made up of one or more files.  
 * Together, these make up the AIP.  AIP files, therefore, have associated metadata that indicate their 
 * membership in a particular AIP (e.g. AIP identifier, version, etc.) and which are are important for 
 * accessing them in storage systems.  The OAR-PDR (at NIST) follows a naming convention that 
 * embeds some of this metadata (which is encasulated in the 
 * {@link gov.nist.oar.bags.preservation.BagUtils BagUtils} class).  This specialized 
 * {@link gov.nist.oar.distrib.FileDescription FileDescription} will add this embedded information as 
 * additional properties.  
 */
public class BagDescription extends FileDescription {

    /**
     * create an empty description
     */
    public BagDescription() { super(); }

    /**
     * initialize this description with basic file information
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     * @param contentType  the MIME type to associate with the bytes
     * @param cs           a Checksum for the bytes on the stream
     */
    public BagDescription(String name, long size, String contentType, Checksum cs) {
        super(name, size, contentType, cs);
        if (name != null)
            addBagPropertiesFor(this, name);
    }

    /**
     * initialize this description with basic file information.  Any String may 
     * be null, and size should be negative if not known.
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     * @param contentType  the MIME type to associate with the bytes
     * @param sha256       the SHA-256 hash of the file contents
     */
    public BagDescription(String name, long size, String contentType, String sha256) {
        this(name, size, contentType, (sha256 == null) ? (Checksum) null : Checksum.sha256(sha256));
    }

    /**
     * initialize this description with a name and size.  
     * @param name         a (file) name for the bytes on the stream
     * @param size         the expected number of bytes available on the stream
     */
    public BagDescription(String name, long size) {
        this(name, size, null, (Checksum) null);
    }

    /**
     * add additional properties to an FileDescription that have been extracted from a 
     * a preservation bag name that follows the OAR bag naming conventions.  If the name 
     * is not a compliant name, this method exits without adding anything.
     * @param desc     the description to add the properties to
     * @param bagname  a preservation bag filename to parse for information.  This can 
     *                 be a file path in which case only the base file name will be 
     *                 examined.
     */
    public static void addBagPropertiesFor(FileDescription desc, String bagname) {
        bagname = (new File(bagname)).getName();
        if (BagUtils.isLegalBagName(bagname)) {
            try {
                List<String> info = BagUtils.parseBagName(bagname);
                desc.aipid = info.get(0);
                if (info.get(1).equals(""))
                    info.set(1, "0");
                desc.setProp("sinceVersion", String.join(".", info.get(1).split("_")));
                desc.setProp("multibagProfileVersion", String.join(".", info.get(2).split("_")));
                desc.setProp("multibagSequence", Long.parseLong(info.get(3)));
                if (! info.get(4).equals("")) {
                    desc.setProp("serialization", info.get(4));
                    if (desc.contentType == null || desc.contentType.equals("")) {
                        if (info.get(4).equals("zip"))
                            desc.contentType = "application/zip";
                        else if (info.get(4).equals("7z"))
                            desc.contentType = "application/x-7z-compressed";
                        else
                            desc.contentType = "application/octet-stream";
                    }
                }
            }
            catch (ParseException ex) {
                // should not happen
            }
        }
    }

}
