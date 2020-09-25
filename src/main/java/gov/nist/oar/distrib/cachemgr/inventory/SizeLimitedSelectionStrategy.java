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

import gov.nist.oar.distrib.cachemgr.DeletionStrategy;
import gov.nist.oar.distrib.cachemgr.CacheObject;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * a {@link gov.nist.oar.distrib.cachemgr.SelectionStrategy} (and 
 * {@link gov.nist.oar.distrib.cachemgr.DeletionStrategy}) implementation that selects 
 * {@link gov.nist.oar.distrib.cachemgr.CacheObject}s up to a set total size.  This is an abstract base 
 * class that requires subclasses to implement the scoring formula into a 
 * {@link #calculateScore(gov.nist.oar.distrib.cachemgr.CacheObject)} method.
 * <p>
 * When passed to the {@link gov.nist.oar.distrib.cachemgr.StorageInventoryDB#selectObjectsFrom(String,gov.nist.oar.distrib.cachemgr.SelectionStrategy) StorageInventoryDB.selectObjectsFrom()}
 * method, this will select {@link gov.nist.oar.distrib.cachemgr.CacheObject}s until the total size just exceeds 
 * a limit set at construction time.  The total size of the selected set may be lower than the limit
 * if cache volume being searched does not contain enough selectable data; if this is the case, 
 * {@link #limitReached()} will return true.  
 */
public abstract class SizeLimitedSelectionStrategy implements DeletionStrategy {

    /**
     * the limit that the total size of all selected Objects can only just exceed 
     */
    protected long sizelimit = 0;

    /**
     * the label of the preferred purpose for generating the candidate CacheObjects.
     */
    protected String purpose = "deletion";

    /**
     * the accumulated size that is nominally needed from the selection.  This is normally less than or 
     * equal to the size limit (which can include some additional slop amount in case all files can't be 
     * used).
     */
    protected long need = 0;
    
    private long totsz = 0;
    private long suffic = 0;

    /**
     * create the strategy with a specified limit
     * @param szlim    the size limit for the selected set (see discussion above).
     * @param purposeLabel  the purpose label for selecting a selection query optimized for 
     *                 this strategy.  If null or empty, "deletion" will be assumed.
     * @param needed   the nominal size that is actually needed in the selection.  This should be 
     *                   less than or equal to szlim.  
     */
    public SizeLimitedSelectionStrategy(long szlim, String purposeLabel, long needed) {
        sizelimit = szlim;
        if (needed < 0) needed = szlim;
        need = needed;
        if (purposeLabel != null && ! purposeLabel.equals(""))
            purpose = purposeLabel;
    }

    /**
     * create the strategy with a specified limit
     * @param szlim    the size limit for the selected set (see discussion above).
     * @param purposeLabel  the purpose label for selecting a selection query optimized for 
     *                 this strategy.  If null or empty, "deletion" will be assumed.
     */
    public SizeLimitedSelectionStrategy(long szlim, String purposeLabel) {
        this(szlim, purposeLabel, szlim);
    }

    /**
     * calculate a score for the given {@link gov.nist.oar.distrib.cachemgr.CacheObject}.  This method 
     * will not set the value into the CacheObject.  
     */
    public abstract double calculateScore(CacheObject co);

    /**
     * apply a selectability score to the given CacheObject.  The implementation is expected 
     * to set the score field of the given CacheObject with the value that is calculated and 
     * returned.  
     */
    @Override
    public double score(CacheObject co) {
        co.score = calculateScore(co);
        long sz = co.getSize();
        if (sz > 0 && co.score > 0) 
            totsz += sz;
        if (suffic < need)
            suffic = totsz;
        return co.score;
    }

    /**
     * return a name identifying the purpose of the selection.  This will be used to select 
     * an appropriate query to the StorageInventoryDB.
     */
    @Override
    public String getPurpose() { return purpose; }

    /**
     * reset the internal sum of the sizes encountered so far via the {@link #score(CacheObject)}
     * method to zero.  The internal sum is used to determine when {@link #limitReached()} should return 
     * true.  
     */
    public void reset() { totsz = 0; }

    /**
     * sort the given list of CacheObjects according to the preferences of this strategy.
     * This default implementation sorts the objects by the score, highest to lowest.
     */
    @Override
    public void sort(List<CacheObject> objs) {
        Collections.sort(objs, (co1, co2) -> {
            return (int) Math.signum(co2.score - co1.score);
        });
    }

    /**
     * return true if the {@link #score(CacheObject) score()} method has seen a set of objects whose 
     * total size just exceeds the limit set at construction time.  
     */
    @Override
    public boolean limitReached() { return (totsz > sizelimit); }

    /**
     * return the total size of all objects passed to the {@link #score(CacheObject) score()} 
     * method.
     */
    public long getTotalSize() { return totsz; }

    /**
     * return the total size the of the portion of the examined selection (provided via 
     * {@link #score(CacheObject) score()}) that sufficiently meets the needed size
     */
    public long getSufficientSize() { return suffic; }

    /**
     * return a new instance of this class configured with a different size limit
     */
    public abstract DeletionStrategy newForSize(long needed, long sizelimit);

    /**
     * clone this instance
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        }
        catch (CloneNotSupportedException ex) {
            // should not happen
            throw new RuntimeException("Internal implementation error: "+ex.getMessage());
        }
    }
}
