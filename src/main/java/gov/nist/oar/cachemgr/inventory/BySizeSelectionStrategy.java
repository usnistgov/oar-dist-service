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

import gov.nist.oar.cachemgr.CacheObject;

/**
 * a {@link gov.nist.oar.cachemgr.SelectionStrategy} implementation that selects the largest 
 * objects up to a specified total size limit.  
 * <p>
 * This is a simple implementation of the {@link SizeLimitedSelectionStrategy} where 
 * {@link #calculateScore(CacheObject)} simply looks at the size of the object.  The 
 * scoring function is linear normalized by a scaling factor set at construction.  
 */
public class BySizeSelectionStrategy extends SizeLimitedSelectionStrategy {

    private double norm = 1.0;

    /**
     * create the strategy with a specified limit
     * @param szlim    the total size limit for selection sets
     */
    public BySizeSelectionStrategy(long szlim) {
        this(szlim, 0.5 * 1e9);
    }

    /**
     * create the strategy with a specified limit
     * @param szlim    the total size limit for selection sets
     * @param normsz   the normalizing size.  This is a size (in bytes) which should 
     *                 receive a score of 1.0.  
     */
    public BySizeSelectionStrategy(long szlim, double normsz) {
        super(szlim, "deletion_s");
        norm = normsz;
    }

    /**
     * calculate a score for the given {@link gov.nist.oar.cachemgr.CacheObject} based on its 
     * size.  This implementation uses the size as the score.
     */
    public double calculateScore(CacheObject co) {
        double sz = 1.0 * co.getSize() / norm;
        return (sz >= 0) ? sz : 0.0;
    }

    /**
     * return the normalizing factor being used when calculating scores.
     */
    public double getNormalizingSize() { return norm; }

    /**
     * return a new instance of this class configured with a different size limit
     */
    @Override
    public SizeLimitedSelectionStrategy newForSize(long newsizelimit) {
        return new BySizeSelectionStrategy(newsizelimit, norm);
    }    
}