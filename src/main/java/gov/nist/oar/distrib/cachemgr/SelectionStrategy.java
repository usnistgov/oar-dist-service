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

import java.util.List;

/**
 * an encapsulation of a strategy for seleting {@link gov.nist.oar.distrib.cachemgr.CacheObject}s for some 
 * purpose (namely, deletion).
 * <p>
 */
public interface SelectionStrategy extends Cloneable {

    /**
     * apply a selectability score to the given CacheObject.  The implementation is expected 
     * to set the score field of the given CacheObject with the value that is calculated and 
     * returned.  
     */
    public double score(CacheObject co);

    /**
     * return a name identifying the purpose of the selection.  This will be used to select 
     * an appropriate query to the StorageInventoryDB.
     */
    public String getPurpose();

    /**
     * reset the internal accumulator that determines when {@link #limitReached()} should return 
     * true.  
     */
    public void reset();

    /**
     * sort the given list of CacheObjects according to the preferences of this strategy.
     */
    public void sort(List<CacheObject> objs);

    /**
     * return true if the {@link #score(CacheObject) score()} method has seen enough objects for a 
     * sufficient selection.
     */
    public boolean limitReached();

    /**
     * create an indepedent copy of this strategy
     */
    public Object clone();
}
