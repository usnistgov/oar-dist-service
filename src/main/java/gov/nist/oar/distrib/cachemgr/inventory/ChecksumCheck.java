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
 *
 * This check supports two construction-time switches which can improve the performance of the test.
 * The first set via the <code>checkLastModified</code> argument (whose value is returned by 
 * {@link #requireUnmodifiedByDate}) will require that the dates of the last modification match between 
 * the given {@link gov.nist.oar.distrib.CacheObject} and the one stored in its volume.  This is based 
 * on the assumption the object's metadata never changes (out-of-band) once it is place in the volume.  
 * When enabled, this check is done before any actual checksum calculation and comparison to ensure a 
 * fast fail. 
 * 
 * The second switch is set via the <code>useVolumeChecksum</code> argument (whose value is returned by
 * {@link #canUseVolumeChecksum}).  If True, this check will attempt to use a volume-provided checksum 
 * as the file's current checksum in lieu of calculating it from the object's byte stream.  Some volumes
 * (namely an {@link gov.nist.oar.distrib.storage.AWSS3CacheVolume} with its "contentMD5" and "etag" 
 * attributes) calculates and stores this value automatically.  If such a checksum is available, it will 
 * be stored in the {@link gov.nist.oar.distrib.CacheObject} metadatum, "volumeChecksum", with a format 
 * "LABEL HASH", where "LABEL" is the name of the checksum algorithm, and "HASH" is the checksum's hash 
 * value.  If this volume-provided hash is available, a checksum is calculated as normal. 
 */
public class ChecksumCheck implements CacheObjectCheck {

    boolean _useVolChkSum = false;
    boolean _checkMod = false;

    /**
     * create a <code>CacheObjectCheck</code> that can run checksum checks
     */
    public ChecksumCheck() { this(false, false); }

    /**
     * create a <code>CacheObjectCheck</code> that can run checksum checks
     * @param checkLastModified       Require that the last modified date has not changed since the object
     *                                was placed in the cache.  This check is done prior to the checksum
     *                                calculation for a fast fail.
     */
    public ChecksumCheck(boolean checkLastModified) { this(checkLastModified, false); }

    /**
     * create a <code>CacheObjectCheck</code> that can run checksum checks
     * @param checkLastModified       Require that the last modified date has not changed since the object
     *                                was placed in the cache.  This check is done prior to the checksum
     *                                calculation for a fast fail.
     * @param leverageVolumeChecksum  if true and the volume supports a remotely stored checksum, this 
     *                                this checksum will be used lieu of recalculating it from the object's 
     *                                byte stream.  Enabling this, of course, will result in a much faster 
     *                                check; however, one must be confident that it is not possible to 
     *                                update the object without updating the volume-provided checksum.  
     *                                It is recommended that <code>checkLastModified</code> be set to true
     *                                if the volume checksum algorithm is less robust than that used by 
     *                                the repository.
     */
    public ChecksumCheck(boolean checkLastModified, boolean useVolumeChecksum) {
        _useVolChkSum = useVolumeChecksum;
        _checkMod = checkLastModified;
    }

    /**
     * return True if this ChecksumCheck allows for leveraging a Volume-provided checksum to take as the
     * object's current checksum, in lieu of recalculating it from the object's byte stream.  If the volume
     * does not provide such a checksum, it will get recalculated.  
     */
    public boolean canUseVolumeChecksum() { return _useVolChkSum; }

    /**
     * return True if the ChecksumCheck will require that the modification times match
     */
    public boolean requireUnmodifiedByDate() { return _checkMod; }

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
        CacheObject vco = co.volume.get(co.name);
        long vsz = vco.getSize();
        long sz = co.getSize();
        if (vsz >= 0 && sz >= 0 && vsz != sz)
            throw new ChecksumMismatchException(co, "size "+Long.toString(vsz), vsz);

        // If required, we make sure the modification date has not changed
        if (requireUnmodifiedByDate())
            _checkLastModified(vco, co);

        // If allowed and available, try using the volume-calculated hash
        if (canUseVolumeChecksum() &&
            co.hasMetadatum("volumeChecksum") && vco.hasMetadatum("volumeChecksum"))
        {
            if (! requireUnmodifiedByDate())
                // force last modified check
                _checkLastModified(vco, co);

            if (! vco.getMetadatumString("volumeChecksum", "-")
                     .equals(co.getMetadatumString("volumeChecksum", "")))
                throw new ChecksumMismatchException(co, vco.getMetadatumString("volumeChecksum", "-"), vsz);

            // we're happy
            return;
        }
        
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

    private void _checkLastModified(CacheObject fromVol, CacheObject fromChkr)
        throws ChecksumMismatchException
    {
        long vmod = fromVol.getLastModified();
        long mod = fromChkr.getLastModified();
        if (mod > 0L && vmod != mod) 
            throw new ChecksumMismatchException(fromChkr, "modified "+Long.toString(vmod), fromVol.getSize());
    }
}
