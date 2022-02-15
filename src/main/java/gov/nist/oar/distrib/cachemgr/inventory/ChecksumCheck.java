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
package gov.nist.oar.distrib.cachemgr.inventory;

import java.io.InputStream;
import java.io.IOException;

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.CacheObjectCheck;
import gov.nist.oar.distrib.cachemgr.IntegrityException;
import gov.nist.oar.distrib.cachemgr.CacheManagementException;
import gov.nist.oar.distrib.Checksum;
import gov.nist.oar.distrib.StorageVolumeException;
import gov.nist.oar.distrib.ObjectNotFoundException;

/**
 * a CacheObjectCheck that will calculate the checksum of an object to ensure it matches the value
 * stored in the object's metadata. 
 */
public class ChecksumCheck implements CacheObjectCheck {

    /**
     * create a <code>CacheObjectCheck</code> that can run checksum checks
     */
    public ChecksumCheck() { }

    /**
     * run the checksum check on an object
     * @throws IntegrityException       if the check was executed successfully but found problem with the 
     *                                  object.
     * @throws ObjectNotFoundException  if the object is not found (perhaps because it was removed) in 
     *                                  volume indicated in the CacheObject
     * @throws StorageVolumeException   if some other error occurs while trying to access the object within 
     *                                  the storage volume
     * @throws CacheManagementException if the check could not be run successfully due to some error other 
     *                                  than a problem access the object from storage.
     */
    @Override
    public void check(CacheObject co)
        throws IntegrityException, StorageVolumeException, CacheManagementException
    {
        // First, check the sizes for expediency
        long vsz = co.volume.get(co.name).getSize();
        long sz = co.getSize();
        if (vsz >= 0 && sz >= 0 && vsz != sz)
            throw new ChecksumMismatchException(co, null, vsz);

        String alg = co.getMetadatumString("checksumAlgorithm", null);
        if (alg == null)
            throw new CacheManagementException("Cache object is missing 'checksumAlgorithm' metadatum");
        String hash = co.getMetadatumString("checksum", null);
        if (hash == null)
            throw new CacheManagementException("Cache object is missing 'checksum' metadatum");

        if (alg.equals(Checksum.SHA256)) {
            try (InputStream is = co.volume.getStream(co.name)) {
                Checksum calc = Checksum.calcSHA256(is);
                if (! hash.equals(calc.hash))
                    throw new ChecksumMismatchException(co, calc.hash, vsz);
            }
            catch (IOException ex) {
                throw new CacheManagementException("IO exception while calculating checksum for " +
                                                   co.volume.getName() + ":" + co.name + ": " +
                                                   ex.getMessage(), ex);
            }
        }
        else {
            throw new CacheManagementException("Unsupported checksum algorithm: "+alg);
        }
    }
}
