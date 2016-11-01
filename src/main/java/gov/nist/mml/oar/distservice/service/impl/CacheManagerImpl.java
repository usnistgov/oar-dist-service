package gov.nist.mml.oar.distservice.service.impl;

import org.springframework.stereotype.Service;

import gov.nist.mml.oar.distservice.service.CacheManager;

@Service
public class CacheManagerImpl implements CacheManager {

	@Override
	public boolean isCached(String dsId, String distId) {
		// TODO Auto-generated method stub
		return false;
	}

}
