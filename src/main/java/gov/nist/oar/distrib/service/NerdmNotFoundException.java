package gov.nist.oar.distrib.service;

import java.io.IOException;

/**
 * Exception thrown when the NERDm metadata for a given dataset ID (dsid)
 * cannot be found (e.g., HTTP 404).
 */
public class NerdmNotFoundException extends IOException {
    public NerdmNotFoundException(String dsid) {
        super("NERDm metadata not found for dsid=" + dsid);
    }
}