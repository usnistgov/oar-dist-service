package gov.nist.oar.distrib.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JwtTokenValidatorTest {

    @Mock
    private RPAConfiguration mockRpaConfiguration;

    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mockRpaConfiguration.getJwtSecretKey()).thenReturn("a-secret-key");
        jwtTokenValidator = new JwtTokenValidator(mockRpaConfiguration);
    }

    @Test
    public void testValidTokenValidation() {
        // Generate a valid token for testing
        String validToken = generateValidToken();

        // Perform token validation
        Map<String, String> tokenDetails = null;
        try {
            tokenDetails = jwtTokenValidator.validate(validToken);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        // Assert the token details
        assertNotNull(tokenDetails);
        assertEquals("Doe, John", tokenDetails.get("userFullname"));
        assertEquals("john.doe@example.com", tokenDetails.get("userEmail"));
        // Additional assertions for other token details
    }

    @Test
    public void testInvalidTokenValidation() {
        // Invalid token for testing
        String invalidToken = "invalid-token";

        try {
            // Perform token validation
            jwtTokenValidator.validate(invalidToken);
            fail("Expected JwtException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof JwtException);
        }
    }

    @Test
    public void testMissingRequiredClaimException() {
        // Generate a token without a required claim (e.g., "user_id")
        String tokenWithMissingClaim = generateTokenWithMissingRequiredClaim();

        try {
            // Perform token validation
            jwtTokenValidator.validate(tokenWithMissingClaim);
            fail("Expected MissingRequiredClaimException to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof MissingRequiredClaimException);
        }
    }

    @Test
    public void testMissingNonRequiredClaimException() {
        // Generate a token without a non required claim (e.g., "user_id")
        String tokenWithMissingClaim = generateTokenWithMissingNonRequiredClaim();

        // Perform token validation
        Map<String, String> tokenDetails = null;
        try {
            tokenDetails = jwtTokenValidator.validate(tokenWithMissingClaim);
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        // Assert that the token details are null, as the validate method returns null when a missing claim is detected
        assertNotNull(tokenDetails);
    }

    private String generateTokenWithMissingRequiredClaim() {
        long expirationTimeMillis = System.currentTimeMillis() + 3600000; // 1 hour from now

        // Generate SecretKey
        String secretKeyString = mockRpaConfiguration.getJwtSecretKey();
        byte[] secretKeyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretKeyBytes, SignatureAlgorithm.HS256.getJcaName());

        // Generate token without the "user_id" claim
        return Jwts.builder()
                // .setSubject("john.doe") // Omit this claim to trigger the exception
                .claim("userName", "John")
                .claim("userLastName", "Doe")
                .claim("userEmail", "john.doe@example.com")
                .claim("exp", expirationTimeMillis)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    private String generateTokenWithMissingNonRequiredClaim() {
        // long expirationTimeMillis = System.currentTimeMillis() + 3600000; // 1 hour from now

        // Generate SecretKey
        String secretKeyString = mockRpaConfiguration.getJwtSecretKey();
        byte[] secretKeyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretKeyBytes, SignatureAlgorithm.HS256.getJcaName());

        // Generate token without the "user_id" claim
        return Jwts.builder()
                .setSubject("john.doe")
                .claim("userName", "John")
                .claim("userLastName", "Doe")
                .claim("userEmail", "john.doe@example.com")
                // .claim("exp", expirationTimeMillis)
                .claim("user_id", 12345)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    private String generateValidToken() {
        long expirationTimeMillis = System.currentTimeMillis() + 3600000; // 1 hour from now

        // Generate SecretKey
        String secretKeyString = mockRpaConfiguration.getJwtSecretKey();
        byte[] secretKeyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretKeyBytes, SignatureAlgorithm.HS256.getJcaName());

        // Generate token
        return Jwts.builder()
                .setSubject("john.doe")
                .claim("userName", "John")
                .claim("userLastName", "Doe")
                .claim("userEmail", "john.doe@example.com")
                .claim("exp", expirationTimeMillis)
                .claim("user_id", 12345)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }
}
