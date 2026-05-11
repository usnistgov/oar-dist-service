package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.web.RPAConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java Keystore implementation of the KeyRetriever interface.
 * This loads a private key from the Java Keystore.
 */
public class JKSKeyRetriever implements KeyRetriever {

    private final static Logger LOGGER = LoggerFactory.getLogger(JKSKeyRetriever.class);
    private static final AtomicBoolean KEYSTORE_LOGGED = new AtomicBoolean(false);

    /**
     * Loads the private key from the Java Keystore given the RPA configuration.
     */
    @Override
    public Key getKey(RPAConfiguration rpaConfiguration)  {
        try {
            if (KEYSTORE_LOGGED.compareAndSet(false, true)) {
                LOGGER.debug("RPA signing keystore initialized path={}",
                        rpaConfiguration.getJksConfig().getKeyStorePath());
            }
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