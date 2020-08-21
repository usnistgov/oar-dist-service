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

/**
 * a {@link gov.nist.oar.distrib.cachemgr.SelectionStrategy} implementation that selects the oldest 
 * data objects for deletion (moderated by its priority).
 * <p>
 * This selection strategy is intended for selecting data objects in a cache to delete so to make 
 * room for new data.  It implements a simple formula for scoring objects based on their age and assigned * priority:
 * <pre>
 *               priority
 *     score = ------------ * age(days)
 *              priority_0
 * </pre>
 * where <code>priority_0</code> is a weighting factor intended to represent the "normal" priority.
 * Priority values smaller than this will be more protected against deletion.  The default value 
 * for <code>priority_0</code> is 10.  Note that this formula will only be applied to files that 
 * are older than a minimum age (see {@link #getMinimumAge()}).  
 */
public class OldSelectionStrategy extends SizeLimitedSelectionStrategy {

    private long now = System.currentTimeMillis();
    private int prinorm = 10;
    private long minage = 3600000;   // 1 hour
    private long unitage = 24 * 3600 * 1000;   // 1 day

    /**
     * create the strategy with a specified limit
     *
     * @param szlim         the total size limit for selection sets
     * @param needed        the nominal size that is actually needed in the selection.  This should be 
     *                        less than or equal to szlim.  
     * @param priority0     the "normal" priority value (see discussion above).  If given value
     *                        is non-positive, te default of 10 is used.
     * @param minAge        the minimum age an object must have to be assigned a non-zero selectability
     *                        score.  This prevents just cached/accessed files from being selected (for
     *                        deletion).
     */
    public OldSelectionStrategy(long szlim, long needed, int priority0, long minAge) {
        this(szlim, needed, priority0);
        minage = minAge;
    }

    /**
     * create the strategy with a specified limit.  The minimum age for a file to be selected 
     * will be one hour.
     *
     * @param szlim         the total size limit for selection sets
     * @param priority0     the "normal" priority value (see discussion above).  If given value
     *                        is non-positive, te default of 10 is used.
     */
    public OldSelectionStrategy(long szlim, long needed, int priority0) {
        super(szlim, "deletion_p", needed);
        if (priority0 > 0.0)
            prinorm = priority0;
    }        

    /**
     * create the strategy with a specified limit.  The minimum age for a file to be selected 
     * will be one hour, and the normal priority will be 10.
     *
     * @param szlim         the total size limit for selection sets
     * @param needed   the nominal size that is actually needed in the selection.  This should be 
     *                   less than or equal to szlim.  
     */
    public OldSelectionStrategy(long szlim, long needed) {
        this(szlim, needed, 0);
    }

    /**
     * create the strategy with a specified limit.  The minimum age for a file to be selected 
     * will be one hour, and the normal priority will be 10.
     *
     * @param szlim         the total size limit for selection sets
     */
    public OldSelectionStrategy(long szlim) {
        this(szlim, szlim, 0);
    }

    /**
     * calculate a score for the given {@link gov.nist.oar.distrib.cachemgr.CacheObject} based on its 
     * size.  This implementation uses the size as the score.
     */
    public double calculateScore(CacheObject co) {
        long age = now - co.getMetadatumLong("since", now);
        if (age < minage) return 0.0;

        return 1.0 * co.getMetadatumInt("priority", prinorm) * age / (unitage * prinorm);
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
     * return the "normal" priority.  This is the default or "normal" priority value typically 
     * assigned to cache objects.  If the assigned priority for an object is less than this value,
     * it will be more protected against selection than "normal" objects.  
     */
    public int getNormalPriority() { return prinorm; }

    /**
     * return the minimum age (in milliseconds) an object must have to be given a non-zero selection 
     * score.  
     */
    public long getMinimumAge() { return minage; }

    /**
     * return a new instance of this class configured with a different size limit
     */
    @Override
    public SizeLimitedSelectionStrategy newForSize(long newsizelimit, long needed) {
        return new OldSelectionStrategy(newsizelimit, needed, getNormalPriority(), getMinimumAge());
    }    
}
