package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.web.RPAConfiguration;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Default implementation of the KeyRetriever interface.
 * This loads a private statically, where the private key is defined in this class as a constant string.
 */
public class DefaultKeyRetriever implements KeyRetriever {

    private static final String KEY = "-----BEGIN PRIVATE KEY-----" +
            "<<<<<PRIVATE_KEY_CONTENT_GOES_HERE>>>>>"+
            "-----END PRIVATE KEY-----";

    @Override
    public Key getKey(RPAConfiguration rpaConfiguration) {
        String privateKey = KEY.replace("-----BEGIN PRIVATE KEY-----", "");
        privateKey = privateKey.replace("-----END PRIVATE KEY-----", "");
        privateKey = privateKey.replaceAll("\\s+", "");

        byte[] decode = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decode);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Exception during key generation");
        }
    }
}