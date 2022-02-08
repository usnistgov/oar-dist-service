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

import gov.nist.oar.distrib.cachemgr.CacheObject;
import gov.nist.oar.distrib.cachemgr.DeletionStrategy;

/**
 * a {@link gov.nist.oar.distrib.cachemgr.DeletionStrategy} implementation that preferntially selects 
 * larger and older data objects.
 * <p>
 * This selection strategy is intended for selecting data objects in a cache to delete so to make 
 * room for new data.  It implements a complex formula for scoring objects that takes into account 
 * size, priority, and time since last access with the goal selecting larger, less requested data 
 * for deletion.  The formula is as follows:
 * <pre>
 *                                   2                
 *     score = priority * ( ------------------- - 1 ) 
 *                          (1 + 8^(-sz/sz_to))       
 *
 *                                age                        1
 *                      * 0.1 * -------- * ( 1 - ------------------------- )
 *                               age_to          (1 + (age/age_to)^4))^0.5
 * </pre>
 *
 * The intended behavior of this formula is to affect the deletability of a file in the following way
 * <ul>
 *   <li> a file that is younger than the "turn-over" age, <code>age_to</code>, (see 
 *        {@link #getTurnOverAge()}) is near zero, making it nearly undeletable; when older than the 
 *        turn-over age, the file's deletability increases linearly with age.  </li>
 *   <li> the deletability of a file increases linearly with size up to a "turn-over" size,
 *        <code>size_to</code> (see {@link #getTurnOverSize()}; files are than this are equally 
 *        deletable (i.e. deletability is near constant with size).  </li>
 *   <li> a file's deletability increases linearly with its priority value (where files with a priority
 *        of zero are undeletable).  The default priority value is 10.  </li>
 * </ul>
 */
public class BigOldSelectionStrategy extends SizeLimitedSelectionStrategy {

    private long now = System.currentTimeMillis();
    private double ageto = 2.5 * 3600000;  // 2.5 hours
    private double szto = 0.5 * 1.0e9;     // 0.5 GB

    /**
     * create the strategy with a specified limit
     * <p>
     * Note that this strategy applies a non-linear weighting to the "age"--the time 
     * since last access--and size.  The age weighting is near zero up to an age turn-over
     * time (in milliseconds), and then is linear.  The size weighting is roughly linear 
     * approximately up to a size turn-over value; after that, it flattens.  These turn-over
     * values can be configured with this constructor.  
     *
     * @param szlim         the total size limit for selection sets
     * @param needed        the nominal size that is actually needed in the selection.  This should be 
     *                        less than or equal to szlim.  
     * @param ageTurnOver   the age of a file at which the scoring weight goes from 
     *                      approximately 0 to linear with age (see description above).
     *                      If given value is non-positive, the default of 2.5 hours is used.
     * @param sizeTurnOver  the size of a file at which the scoreing weight goes from 
     *                      linear with size to flat (see description above).  
     *                      If given value is non-positive, the default of 0.5 GB is used.
     */
    public BigOldSelectionStrategy(long szlim, long needed, double ageTurnOver, double sizeTurnOver) {
        super(szlim, "deletion_s", needed);
        if (ageTurnOver > 0.0)
            ageto = ageTurnOver;
        if (sizeTurnOver > 0.0)
            szto = sizeTurnOver;
    }

    /**
     * create the strategy with a specified limit
     * <p>
     * Note that this strategy applies a non-linear weighting to the "age"--the time 
     * since last access--and size.  The age weighting is near zero up to an age turn-over
     * time (in milliseconds), and then is linear.  The size weighting is roughly linear 
     * approximately up to a size turn-over value; after that, it flattens.  These turn-over
     * values can be configured with this constructor.  
     *
     * @param szlim         the total size limit for selection sets
     * @param ageTurnOver   the age of a file at which the scoring weight goes from 
     *                      approximately 0 to linear with age (see description above).
     *                      If given value is non-positive, the default of 2.5 hours is used.
     * @param sizeTurnOver  the size of a file at which the scoreing weight goes from 
     *                      linear with size to flat (see description above).  
     *                      If given value is non-positive, the default of 0.5 GB is used.
     */
    public BigOldSelectionStrategy(long szlim, double ageTurnOver, double sizeTurnOver) {
        this(szlim, szlim, ageTurnOver, sizeTurnOver);
    }

    /**
     * create the strategy with a specified limit.  The turn-over time will be 2.5 hours, and the 
     * turn-over size will 0.5 Gb.
     * @param szlim         the total size limit for selection sets
     */
    public BigOldSelectionStrategy(long szlim) {
        this(szlim, szlim);
    }


    /**
     * create the strategy with a specified limit.  The turn-over time will be 2.5 hours, and the 
     * turn-over size will 0.5 Gb.
     * @param szlim         the total size limit for selection sets
     * @param needed        the nominal size that is actually needed in the selection.  This should be 
     *                        less than or equal to szlim.  
     */
    public BigOldSelectionStrategy(long szlim, long needed) {
        super(szlim, "deletion_s", needed);
    }

    /**
     * calculate a score for the given {@link gov.nist.oar.distrib.cachemgr.CacheObject} based on its 
     * size.  This implementation uses the size as the score.
     */
    public double calculateScore(CacheObject co) {

        // time dependence:
        // age = ms since last access
        // f(age) ~ 0  in range 0 < age < 2.5 hours
        // f(age) -> linear for age > 5 hours
        long age = now - co.getMetadatumLong("since", now);
        double fage = 0.1 * age * (1 - 1/Math.sqrt((1 + Math.pow(age / (2 * ageto), 4)))) / ageto;

        // size dependence:
        // f(sz) -> linear for sz < 0.5 GB
        long sz = co.getSize();
        double fsz = (2 /(1 + Math.pow(8, -1.0 * sz / szto))) - 1;
        
        return fage * fsz * co.getMetadatumInt("priority", 10);
    }

    /**
     * reset the internal sum of the sizes encountered so far via the {@link #score(CacheObject)}
     * method to zero.  The internal sum is used to determine when {@link #limitReached()} should return 
     * true.  This implementation also resets the timestamp marking "now".  
     */
    @Override
    public void reset() {
        super.reset();
        now = System.currentTimeMillis();
    }

    /**
     * return the configured turn-over age.  This value is the age (time since last access, in ms) 
     * where the score weighting goes from near zero to linear with age.
     */
    public double getTurnOverAge() { return ageto; }

    /**
     * return the configure turn-over file size.  This value is the file size (in bytes) where the 
     * score weighting goes from linear with size to flat.
     */
    public double getTurnOverSize() { return szto; }
    

    /**
     * return a new instance of this class configured with a different size limit
     */
    @Override
    public DeletionStrategy newForSize(long needed, long newsizelimit) {
        return new BigOldSelectionStrategy(newsizelimit, needed, getTurnOverAge(), getTurnOverSize());
    }    
}
