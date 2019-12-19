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
package gov.nist.oar.cachemgr;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * a simple container class used to deliver a checksum hash, labeled with the 
 * the algorithm that was used to calculate it.  A static utility function can also calculate a 
 * SHA-256 checksum for a file.
 */
public class Checksum {

    /**
     * the SHA-256 checksum algorithm
     */
    public static String SHA256 = "sha256";

    /**
     * the CRC-32 checksum algorithm (used in zip files)
     */
    public static String CRC32 = "crc32";

    /**
     * the checksum hash value
     */
    public String hash = null;

    /**
     * the algorithm used to calculate the hash
     */
    public String algorithm = null;

    /**
     * initialize the checksum value
     * @param hash   the checksum hash value
     * @param alg    the name of the algorithm used to calculate the hash.  
     *               It is recommended that the usual filename extension for
     *               hash files of this type be used as the name.
     */
    public Checksum(String hash, String alg) {
        this.hash = hash;
        this.algorithm = alg;
    }

    /**
     * create a SHA-256 checksum 
     */
    public static Checksum SHA256(String hash) {
        return new Checksum(hash, Checksum.SHA256);
    }

    /**
     * calculate the SHA-256 checksum of a file.  
     *
     * @param ds    an open stream set at the beginning of the file
     */
    public static Checksum calcSHA256(InputStream ds) throws IOException {
        MessageDigest md = null;
        
        try {
            md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[50000];
            int nr = 0;
            while ( (nr = ds.read(buf)) >= 0 ) 
                md.update(buf, 0, nr);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Unexpected Java configuration: SHA-256 algorithm not supported!");
        }

        return SHA256(bytesToHex(md.digest()));
    }

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    
}
