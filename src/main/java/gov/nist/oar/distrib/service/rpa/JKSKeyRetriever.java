package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.web.RPAConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;

public class JKSKeyRetriever implements KeyRetriever {

    private final static Logger LOGGER = LoggerFactory.getLogger(JKSKeyRetriever.class);
    @Override
    public Key getKey(RPAConfiguration rpaConfiguration)  {
        try {
            LOGGER.info("Using Java Keystore located at " + rpaConfiguration.getJksConfig().getKeyStorePath());
            KeyStore keystore = KeyStore.getInstance(rpaConfiguration.getJksConfig().getKeyStoreType());
            keystore.load(Files.newInputStream(
                            Paths.get(rpaConfiguration.getJksConfig().getKeyStorePath())),
                    rpaConfiguration.getJksConfig().getKeyStorePassword().toCharArray());
            return keystore.getKey(
                    rpaConfiguration.getJksConfig().getKeyAlias(),
                    rpaConfiguration.getJksConfig().getKeyPassword().toCharArray()
            );
        } catch (Exception e) {
            throw new RuntimeException("Exception during key generation - " + e.getMessage());
        }
    }
}