package gov.nist.oar.distrib.web;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JwtTokenValidatorTest {

    @Mock
    private RPAConfiguration mockRpaConfiguration;

    private JwtTokenValidator jwtTokenValidator;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
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
            throw new RuntimeException(e);
        }

        // Assert the token details
        assertNotNull(tokenDetails);
        assertEquals("John Doe", tokenDetails.get("username"));
        assertEquals("john.doe@example.com", tokenDetails.get("email"));
        // Additional assertions for other token details
    }

    @Test
    public void testInvalidTokenValidation() {
        // Invalid token for testing
        String invalidToken = "invalid-token";

        // Perform token validation
        Map<String, String> tokenDetails = null;
        try {
            tokenDetails = jwtTokenValidator.validate(invalidToken);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Assert that the token is invalid
        assertNull(tokenDetails);
    }

    private String generateValidToken() {
        long expirationTimeMillis = System.currentTimeMillis() + 3600000; // 1 hour from now

        return Jwts.builder()
                .setSubject("john.doe")
                .claim("user_name", "John Doe")
                .claim("email", "john.doe@example.com")
                .claim("exp", expirationTimeMillis)
                .claim("user_id", 12345)
                .signWith(SignatureAlgorithm.HS256, mockRpaConfiguration.getJwtSecretKey())
                .compact();
    }

}

