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
package gov.nist.oar.distrib.cachemgr.simple;

import java.util.Collection;

import org.slf4j.Logger;

import gov.nist.oar.distrib.cachemgr.BasicCache;
import gov.nist.oar.distrib.cachemgr.CacheVolume;
import gov.nist.oar.distrib.cachemgr.DeletionPlanner;
import gov.nist.oar.distrib.cachemgr.StorageInventoryDB;
import gov.nist.oar.distrib.cachemgr.inventory.DefaultDeletionPlanner;
import gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy;

/**
 * a simple implementation of a {@link gov.nist.oar.distrib.cachemgr.Cache}.  
 * <p>
 * The cache is fully configured at construction time.  The user provides it with a set of 
 * {@link gov.nist.oar.distrib.cachemgr.CacheVolume}s and a 
 * {@link gov.nist.oar.distrib.cachemgr.StorageInventoryDB}.  If a 
 * {@link gov.nist.oar.distrib.cachemgr.DeletionPlanner} is not also provided, a default 
 * that uses the {@link gov.nist.oar.distrib.cachemgr.inventory.OldSelectionStrategy} as 
 * its strategy for deleting files which deletes old files first.
 */
public class SimpleCache extends BasicCache {

    private DeletionPlanner defplnr = null;
    private DeletionPlanner plnr = null;
    
    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use {@link #SimpleCache(String,StorageInventoryDB,Collection,DeletionPlanner)}.
     * 
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb       the inventory database to use
     * @param volcount  the expected number of CacheVolumes that will be attached via addVolume()
     * @param delplanner  the DeletionPlanner to use by default (until setDeletionPlanner() is called)
     * @param log       a particular Logger instance that should be used.  If null, a default one
     *                    will be created.  
     */
    public SimpleCache(String name, StorageInventoryDB idb, int volcount, DeletionPlanner delplanner,
                       Logger log)
    {
        super(name, idb, volcount, log);
        _setDefPlanner(delplanner);
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb      the inventory database to use
     * @param vols     the CacheVolumes to attach to this cache
     * @param delplanner  the DeletionPlanner to use by default (until setDeletionPlanner() is called)
     * @param log      a particular Logger instance that should be used.  If null, a default one
     *                   will be created.  
     */
    public SimpleCache(String name, StorageInventoryDB idb, Collection<CacheVolume> vols,
                       DeletionPlanner delplanner, Logger log)
    {
        super(name, idb, vols, log);
        _setDefPlanner(delplanner);
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb      the inventory database to use
     * @param vols     the CacheVolumes to attach to this cache
     * @param delplanner  the DeletionPlanner to use by default (until setDeletionPlanner() is called)
     */
    public SimpleCache(String name, StorageInventoryDB idb, Collection<CacheVolume> vols,
                       DeletionPlanner delplanner)
    {
        this(name, idb, vols, delplanner, null);
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb      the inventory database to use
     * @param vols     the CacheVolumes to attach to this cache
     * @param log      a particular Logger instance that should be used.  If null, a default one
     *                   will be created.  
     */
    public SimpleCache(String name, StorageInventoryDB idb, Collection<CacheVolume> vols, Logger log) {
        this(name, idb, vols, null, log);
    }

    /**
     * create the Cache around pre-popluated volumes and inventory database.  This constructor is 
     * for recreating an instance of a Cache around previously persisted volumes and associated 
     * database.  Thus, the volumes and their contents should already be registered with the 
     * associated database.  
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb      the inventory database to use
     * @param vols     the CacheVolumes to attach to this cache
     */
    public SimpleCache(String name, StorageInventoryDB idb, Collection<CacheVolume> vols) {
        this(name, idb, vols, (Logger) null);
    }

    private void _setDefPlanner(DeletionPlanner delplanner) {
        if (delplanner == null) {
            OldSelectionStrategy ss = new OldSelectionStrategy(1000);
            delplanner = new DefaultDeletionPlanner(db, volumes.values(), ss);
        }
        defplnr = delplanner;
        setDeletionPlanner(defplnr);
    }        

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use {@link #SimpleCache(String,StorageInventoryDB,Collection,DeletionPlanner)}.
     * 
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb       the (empty) inventory database to use
     * @param delplanner  the DeletionPlanner to use by default (until setDeletionPlanner() is called)
     */
    public SimpleCache(String name, StorageInventoryDB idb, DeletionPlanner delplanner) {
        this(name, idb, 2, delplanner, null);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link #SimpleCache(String,StorageInventoryDB,Collection)}.
     * 
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb   the (empty) inventory database to use
     * @param delplanner  the DeletionPlanner to use by default (until setDeletionPlanner() is called)
     * @param log   a particular Logger instance that should be used.  If null, a default one
     *                will be provided.  
     */
    public SimpleCache(String name, StorageInventoryDB idb, DeletionPlanner delplanner, Logger log) {
        this(name, idb, 2, delplanner, log);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link #SimpleCache(String,StorageInventoryDB,Collection)}.
     * 
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb   the (empty) inventory database to use
     * @param log   a particular Logger instance that should be used.  If null, a default one
     *                will be provided.  
     */
    public SimpleCache(String name, StorageInventoryDB idb, Logger log) {
        this(name, idb, 2, null, log);
    }

    /**
     * create the Cache without volumes.  The provided inventory database should be empty of 
     * object records and with no volumes registered.  To create a Cache with a prepopulated 
     * database, use 
     * {@link #SimpleCache(String,StorageInventoryDB,Collection)}.
     * 
     * @param name   a name to give this cache.  In a system with multiple caches with 
     *               different roles this provides a way to distinguish between the different 
     *               caches in messages.
     * @param idb   the (empty) inventory database to use
     */
    public SimpleCache(String name, StorageInventoryDB idb) {
        this(name, idb, 2, null, null);
    }

    /**
     * return a deletion planner for a particular use.  As this implementation uses only a single
     * deletion planner at a time, the preferences argument is ignored.
     * @param preferences  an and-ed set of bits indicating what the space will be used for.  In 
     *                     this implementation, this argument is ignored; the currently set planner
     *                     is always returned.  
     */
    @Override
    protected DeletionPlanner getDeletionPlanner(int preferences) {
        return getDeletionPlanner();
    }

    /**
     * set the deletion planner to use when clearing space for new files.  A null value resets the
     * planner to the default set at construction time.
     */
    public void setDeletionPlanner(DeletionPlanner planner) {
        if (planner == null)
            planner = defplnr;
        plnr = planner;
    }

    /**
     * return the currently set deletion planner
     */
    public DeletionPlanner getDeletionPlanner() {
        return plnr;
    }
}

 
