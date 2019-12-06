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
package gov.nist.oar.cachemgr.inventory;

import gov.nist.oar.cachemgr.SelectionStrategy;
import gov.nist.oar.cachemgr.CacheObject;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * a {@link gov.nist.oar.cachemgr.SelectionStrategy} implementation that selects 
 * {@link gov.nist.oar.cachemgr.CacheObject}s up to a set total size.  This is an abstract base 
 * class that requires subclasses to implement the scoring formula into a 
 * {@link #calculateScore(gov.nist.oar.cachemgr.CacheObject)} method.
 * <p>
 * When passed to the {@link gov.nist.oar.cachemgr.StorageInventoryDB#selectObjectsFrom(String,gov.nist.oar.cachemgr.SelectionStrategy) StorageInventoryDB.selectObjectsFrom()}
 * method, this will select {@link gov.nist.oar.cachemgr.CacheObject}s until the total size just exceeds 
 * a limit set at construction time.  The total size of the selected set may be lower than the limit
 * cache volume being searched does not contain enough selectable data.  
 */
public abstract class SizeLimitedSelectionStrategy implements SelectionStrategy {

    /**
     * the limit that the total size of all selected Objects can only just exceed 
     */
    protected double sizelimit = 0;

    /**
     * the label of the preferred purpose for generating the candidate CacheObjects.
     */
    protected String purpose = "deletion";

    private long totsz = 0;

    /**
     * create the strategy with a specified limit
     * @param szlim    the size limit for the selected set (see discussion above).
     * @param purposeLabel  the purpose label for selecting a selection query optimized for 
     *                 this strategy.  If null or empty, "deletion" will be assumed.
     */
    public SizeLimitedSelectionStrategy(long szlim, String purposeLabel) {
        sizelimit = szlim;
        if (purposeLabel != null && ! purposeLabel.equals(""))
            purpose = purposeLabel;
    }

    /**
     * calculate a score for the given {@link gov.nist.oar.cachemgr.CacheObject}.  This method 
     * will not set the value into the CacheObject.  
     */
    public abstract double calculateScore(CacheObject co);

    /**
     * apply a selectability score to the given CacheObject.  The implementation is expected 
     * to set the score field of the given CacheObject with the value that is calculated and 
     * returned.  
     */
    public double score(CacheObject co) {
        co.score = calculateScore(co);
        long sz = co.getSize();
        if (sz > 0) 
            totsz += sz;
        return co.score;
    }

    /**
     * return a name identifying the purpose of the selection.  This will be used to select 
     * an appropriate query to the StorageInventoryDB.
     */
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
    public void sort(List<CacheObject> objs) {
        Collections.sort(objs, (co1, co2) -> {
            return (int) Math.signum(co2.score - co1.score);
        });
    }

    /**
     * return true if the {@link #score(CacheObject) score()} method has seen a set of objects whose 
     * total size just exceeds the limit set at construction time.  
     */
    public boolean limitReached() { return (totsz > sizelimit); }

    /**
     * return the total size of all objects passed to the {@link #score(CacheObject) score()} 
     * method.
     */
    public long getTotalSize() { return totsz; }

    /**
     * return a new instance of this class configured with a different size limit
     */
    public abstract SizeLimitedSelectionStrategy newForSize(long newsizelimit);
}
