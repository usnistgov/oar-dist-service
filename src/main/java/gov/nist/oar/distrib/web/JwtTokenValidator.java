package gov.nist.oar.distrib.web;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for validating and extracting token details from JWT tokens.
 */
public class JwtTokenValidator {

    private final RPAConfiguration rpaConfiguration;

    /**
     * Constructs a new JwtTokenValidator with the provided configuration.
     *
     * @param configuration the RPAConfiguration object containing the secret key
     */
    public JwtTokenValidator(RPAConfiguration configuration) {
        this.rpaConfiguration = configuration;
    }

    /**
     * Validates the JWT token and extracts the token details.
     *
     * @param token the JWT token to be validated
     * @return a Map containing the extracted token details, or null if the token is invalid or expired
     * @throws RuntimeException if there is an error while processing the token
     */
    public Map<String, String> validate(String token) throws Exception {
        try {
            // Generate SecretKey
            String secretKeyString = rpaConfiguration.getJwtSecretKey();
            byte[] secretKeyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
            SecretKey secretKey = new SecretKeySpec(secretKeyBytes, SignatureAlgorithm.HS256.getJcaName());
            // Parse token
            Jws<Claims> jws = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token);

            Claims claims = jws.getBody();
            Map<String, String> tokenDetails = new HashMap<>();
            String userFullname = claims.get("userLastName", String.class) + ", " + claims.get("userName", String.class);
            tokenDetails.put("userFullname", userFullname );
            tokenDetails.put("userEmail", claims.get("userEmail", String.class));
            tokenDetails.put("expiry", claims.get("exp").toString());
            tokenDetails.put("user_id", claims.get("user_id").toString());

            return tokenDetails;

        } catch (JwtException ex) {
            // If the token is expired or signature does not match, it will throw an Exception
            return null;
        } catch (Exception ex) {
            throw new RuntimeException("Error while processing the token: " + ex.getMessage());
        }
    }
}
