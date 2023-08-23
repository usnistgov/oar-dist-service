package gov.nist.oar.distrib.web;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a utility for validating JWT (JSON Web Tokens) and extracting user details from them.
 * <p>
 * The JwtTokenValidator ensures the JWT's signature matches the expected secret key and is within the validity period.
 * Upon successful validation, it extracts the user's details, such as full name, email, expiry time, and user ID,
 * from the token's claims.
 */
public class JwtTokenValidator {

    /**
     * An instance of {@link RPAConfiguration} that includes the JWT secret key parameter.
     */
    private final RPAConfiguration rpaConfiguration;

    /**
     * Logger for this class.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(JwtTokenValidator.class);

    /**
     * Constructs a new JwtTokenValidator with the provided configuration.
     *
     * @param configuration the RPAConfiguration object containing the secret key
     */
    public JwtTokenValidator(RPAConfiguration configuration) {
        this.rpaConfiguration = configuration;
    }

    /**
     * Validates the provided JWT token, extracts the details, and returns them in a Map.
     * If the token is invalid or expired, it returns null. If any other exception occurs during
     * processing, a RuntimeException is thrown.
     * <p>
     * The token is validated against the secret key using HS256 signature algorithm. The following
     * token details are extracted: user's full name, email, token expiry, and user_id.
     * <p>
     * TODO: add more details to the map
     *
     * @param token The JWT token to be validated and from which to extract details.
     * @return A Map containing the extracted token details, or null if the token is invalid or expired.
     * @throws RuntimeException If there is an error while processing the token.
     */
    public Map<String, String> validate(String token) throws JwtException, MissingClaimException  {
        try {
            // Retrieve the secret key string from the configuration, convert it to bytes using UTF-8 encoding,
            // and then create a SecretKey object using these bytes using the HS256 signature algorithm.
            String secretKeyString = rpaConfiguration.getJwtSecretKey();
            byte[] secretKeyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
            SecretKey secretKey = new SecretKeySpec(secretKeyBytes, SignatureAlgorithm.HS256.getJcaName());

            // Use the JJWT parser to validate the JWT token and parse the claims.
            // If the token's signature matches the provided secretKey, a Jws<Claims> object containing the token's
            // details is returned.
            Jws<Claims> jws = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token);

            // Extract the JWT claims and create a map of the token details, including user full name, email, expiry
            // time, and user ID.
            Claims claims = jws.getBody();
            Map<String, String> tokenDetails = new HashMap<>();
            String userFullname = getClaimAsString(claims.get("userLastName"), "userLastName") + ", " +
                            getClaimAsString(claims.get("userName"), "userName");
            tokenDetails.put("userFullname", userFullname);
            tokenDetails.put("userEmail", getClaimAsString(claims.get("userEmail"), "userEmail"));
            tokenDetails.put("expiry", getClaimAsString(claims.get("exp"), "exp"));
            tokenDetails.put("user_id", getClaimAsString(claims.getSubject(), "user_id"));

            LOGGER.debug("Token successfully validated and details extracted.");

            return tokenDetails;

        } catch (MissingClaimException e) {
            // Handle the specific case of a missing claim
            String missingClaimName = e.getMissingClaimName();
            LOGGER.warn("Missing claim detected: " + missingClaimName);
            return null;
        } catch (JwtException ex) {
            // If the token is expired or signature does not match, it will throw an Exception
            LOGGER.debug("Token validation failed due to JwtException: ", ex);
            return null;
        } catch (Exception ex) {
            LOGGER.error("Error while processing the token: ", ex);
            throw new RuntimeException("Error while processing the token: " + ex.getMessage());
        }
    }

    /**
     * Retrieves the string representation of a claim object. If the object is null, logs a warning and throws a
     * MissingClaimException.
     *
     * @param obj       The object to be converted to a string. Represents the value of a JWT claim.
     * @param fieldName The name of the field or claim being processed. Used in the warning and exception messages.
     * @return The string representation of the object if it's not null.
     * @throws MissingClaimException If the object is null, indicating that the claim is missing from the token.
     */
    private String getClaimAsString(Object obj, String fieldName) throws MissingClaimException {
        if (obj != null) {
            return obj.toString();
        } else {
            throw new MissingClaimException(fieldName);
        }
    }

}
