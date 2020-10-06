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
 * a container for capturing the configuration of a {@link CacheVolume} for its use in a 
 * {@link ConfigurableCache}.  
 * <p>
 * This class captures the following configuration to be associated with one or more volumes:
 * <ul>
 *   <li> role identifiers to associate with volumes that will cause them to be selected via the 
 *        <code>preferences</code> parameter in the {@link Cache#reserveSpace(long, int) reserveSpace()} 
 *        function. </li>
 *   <li> a particular {@link SelectionStrategy} to use when clearing space in a volume </li>
 *   <li> the status of the volume (e.g. whether it is disabled) </li>
 * </ul>
 */
public class VolumeConfig {

    protected DeletionStrategy delstrat = null;
    protected int roles = 0;
    protected Integer status = null;  // null means keep previous value or use default (usually UPDATE)

    /**
     * Create an empty instance
     */
    public VolumeConfig() { }

    /**
     * Create an empty instance with an initial status code
     * @param stat   the status code, normally one of the constants defined in {@link VolumeStatus}
     */
    public VolumeConfig(int stat) {
        status = stat;
    }

    /**
     * return the current status set for this configuration.  A null value indicates that the 
     * previously set value or the default should be used.  
     */
    public Integer getStatus() { return status; }

    /**
     * set the status for this configuration
     * @param stat   the status code, normally one of the constants defined in {@link VolumeStatus}
     */
    public void setStatus(int stat) { status = new Integer(stat); }

    /**
     * set the status for this configuration.  This version allows null to be provided as a value.
     * @param stat   the status code, normally one of the constants defined in {@link VolumeStatus};
     *               if null, the previously set value will be retained or otherwise the default will 
     *               be used.  
     */
    public void setStatus(Integer stat) { status = stat; }

    /** 
     * replace the currently set status with a new one
     * @param stat   the status code, normally one of the constants defined in {@link VolumeStatus}
     */
    public VolumeConfig withStatus(int stat) {
        setStatus(stat);
        return this;
    }

    /** 
     * replace the currently set status with a new one.  This version allows null to be provided as a value.
     * @param stat   the status code, normally one of the constants defined in {@link VolumeStatus};
     *               if null, the previously set value will be retained or otherwise the default will 
     *               be used.  
     */
    public VolumeConfig withStatus(Integer stat) {
        setStatus(stat);
        return this;
    }

    /**
     * return the current volume roles set for this configuration
     * @return int -- a bit-wise AND-ed set of role codes.  
     */
    public int getRoles() { return roles; }

    /**
     * set the roles for this configuration
     * {@see #addRoles(int)}
     * {@see #withRoles(int)}
     * @param roles   the bit-wise AND-ed set of role codes to assign
     */
    public void setRoles(int roles) { this.roles = roles; }

    /**
     * add additional roles to those already set in this configuration
     * {@see #setRoles(int)}
     * {@see #withRoles(int)}
     * @param roles   the bit-wise AND-ed set of role codes to add.
     * @return int -- the complete set of bit-wise AND-ed role codes assigned after adding the input roles.
     */
    public int addRoles(int roles) {
        this.roles &= roles;
        return roles;
    }

    /** 
     * replace the currently set roles with a new one
     * @param roles   a bit-wise AND-ed set of role codes to assign
     */
    public VolumeConfig withRoles(int roles) {
        setRoles(roles);
        return this;
    }

    /**
     * return the SelectionStrategy instance to be used for selecting deletable objects 
     */
    public DeletionStrategy getDeletionStrategy() { return delstrat; }

    /**
     * set the SelectionStrategy instance to be used for selecting deletable objects
     */
    public void setDeletionStrategy(DeletionStrategy strategy) { delstrat = strategy; }

    /** 
     * replace the SelectionStrategy instance to be used for selecting deletable objects
     */
    public VolumeConfig withDeletionStrategy(DeletionStrategy strategy) {
        setDeletionStrategy(strategy);
        return this;
    }
}
