package gov.nist.oar.distrib.web;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

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
            Jws<Claims> jws = Jwts.parser()
                    .setSigningKey(rpaConfiguration.getJwtSecretKey())
                    .parseClaimsJws(token);

            Claims claims = jws.getBody();
            Map<String, String> tokenDetails = new HashMap<>();
            tokenDetails.put("username", claims.get("user_name", String.class));
            tokenDetails.put("email", claims.get("email", String.class));
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
