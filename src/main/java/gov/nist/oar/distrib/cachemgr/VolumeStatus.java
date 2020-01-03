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
package gov.nist.oar.distrib.cachemgr;

/**
 * Constants for indicating the status of a storage volume.
 */
public interface VolumeStatus {

    /**
     * a volume status indicating that the volume is disabled
     */
    public final int VOL_DISABLED = 0;

    /**
     * a volume status indicating that the volume is enabled only for getting object info.
     * (E.g. size, checksum, status.)
     */
    public final int VOL_FOR_INFO = 1;

    /**
     * a volume status indicating that the volume is enabled only for retrieving objects.
     * (Object info is also available--e.g. size, checksum, status.)
     */
    public final int VOL_FOR_GET = 2;

    /**
     * a volume status indicating that the volume is enabled updates (adding and removing objects).
     * (Getting objects or object info is also possible.)
     */
    public final int VOL_FOR_UPDATE = 3;

}



