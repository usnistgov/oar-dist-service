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
 * an encapsulation of a strategy for selecting {@link gov.nist.oar.distrib.cachemgr.CacheObject}s 
 * specifically for deletion.  
 * <p>
 * A deletion strategy includes concepts of minimum and maximum sizes of the selection to achieve.  
 * The former is the minimum number of bytes required to be selected (to be ultimately deleted) 
 * necessary to meet the user's needs.  The latter is a total size limit that, once exceeded, halts 
 * further selection.  The size limit should be larger than minimum size; it is intended to provide 
 * extra files that can be deleted if necessary (e.g. in case there is a problem 
 * removing other objects).  
 * <p>
 */
public interface DeletionStrategy extends SelectionStrategy {

    /**
     * return the total size of all objects passed to the {@link #score(CacheObject) score()} 
     * method so far.  When this value exceeds the size limit set at construction time, 
     * {@link #limitReached()} will return true.
     */
    public long getTotalSize();

    /**
     * return the total size the of the portion of the examined selection (provided via 
     * {@link #score(CacheObject) score()}) that sufficiently meets the needed size.  If 
     * {@link #limitReached()} returns true, then this value should exceed the minimum size 
     * set at construction.  
     */
    public long getSufficientSize();

    /**
     * return a new instance of this class configured with a different size limit.
     * @param minsize    the minimal number of bytes required to be part of the selection
     * @param sizelimit  a limit in the total number of bytes that should be selected.  This
     *                    number should be larger than minsize.  
     */
    public abstract DeletionStrategy newForSize(long minsize, long sizelimit);
}

    
