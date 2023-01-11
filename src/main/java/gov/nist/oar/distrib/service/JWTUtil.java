package gov.nist.oar.distrib.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gov.nist.oar.distrib.service.rpa.SalesforceToken;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JWTUtil {

    public static String BEARER_TOKEN_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    public static String OAUTH2_TOKEN_URL = "https://nist--devpro.sandbox.my.salesforce.com/services/oauth2/token";
    public static String CLAIM_TEMPLATE = "'{'\"iss\": \"{0}\", \"sub\": \"{1}\", \"aud\": \"{2}\", \"exp\": \"{3}\"'}'";

    private static final RestTemplate restTemplate = null;


    public static void main(String[] args) throws Exception {
//        String accessToken = getAccessToken();
//        System.out.println("access_token=" + accessToken);
        String assertion = makeAssertion();
        SalesforceToken token = sendTokenRequest(assertion);
    }

    private static SalesforceToken sendTokenRequest(String assertion) {
        String url = UriComponentsBuilder.fromUriString("https://nist--devpro.sandbox.my.salesforce.com")
                .path("/services/oauth2/token")
                .queryParam("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .queryParam("assertion", assertion)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForObject(url, new HttpEntity<>(null, headers), SalesforceToken.class);
    }
    
    public static String getAccessToken() throws Exception {
        // Login
        final CloseableHttpClient httpclient = HttpClients.createDefault();
        final List<NameValuePair> loginParams = new ArrayList<>();
        loginParams.add(new BasicNameValuePair("grant_type", BEARER_TOKEN_GRANT_TYPE));
        String assertion = makeAssertion();
        loginParams.add(new BasicNameValuePair("assertion", assertion));

        String postUrl = OAUTH2_TOKEN_URL;
        final HttpPost post = new HttpPost(postUrl);

        post.setEntity(new UrlEncodedFormEntity(loginParams));

        final HttpResponse loginResponse = httpclient.execute(post);
        // parse
        final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        final JsonNode loginResult = mapper.readValue(loginResponse.getEntity().getContent(), JsonNode.class);

        if (loginResponse.toString().contains("Bad Request")) {
            throw new Exception(loginResult.get("error").asText() + " - " + loginResult.get("error_description").asText());
        }
        System.out.println("loginResult: " + loginResult);
        return loginResult.get("access_token").asText();
    }


    public static String makeAssertion(String customerKey, String username, String instanceUrl, String keystorePath,
                                       String keystorePassword, String certAlias, String certPassword) {
        String header = "{\"alg\":\"RS256\"}";
        try {
            StringBuffer token = new StringBuffer();

            // Encode the JWT Header and add it to our string to sign
            token.append(Base64.encodeBase64URLSafeString(header.getBytes("UTF-8")));

            // Separate with a period
            token.append(".");

            // Create the JWT Claims Object
            String[] claimArray = new String[4];
            claimArray[0] = customerKey;
            claimArray[1] = username;
            claimArray[2] = instanceUrl;
            claimArray[3] = Long.toString((System.currentTimeMillis() / 1000) + 300);
            MessageFormat claims;
            claims = new MessageFormat(CLAIM_TEMPLATE);
            String payload = claims.format(claimArray);

            // Add the encoded claims object
            token.append(Base64.encodeBase64URLSafeString(payload.getBytes("UTF-8")));

            // Load the private key from a keystore
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(Files.newInputStream(Paths.get(keystorePath)), keystorePassword.toCharArray());
            PrivateKey privateKey = (PrivateKey) keystore.getKey(certAlias, certPassword.toCharArray());

            // Sign the JWT Header + "." + JWT Claims Object
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(token.toString().getBytes("UTF-8"));
            String signedPayload = Base64.encodeBase64URLSafeString(signature.sign());

            // Separate with a period
            token.append(".");

            // Add the encoded signature
            token.append(signedPayload);

            return token.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String makeAssertion() {
        String header = "{\"alg\":\"RS256\"}";
        String claimTemplate = "'{'\"iss\": \"{0}\", \"sub\": \"{1}\", \"aud\": \"{2}\", \"exp\": \"{3}\"'}'";

        try {
            StringBuffer token = new StringBuffer();

            // Encode the JWT Header and add it to our string to sign
            token.append(Base64.encodeBase64URLSafeString(header.getBytes("UTF-8")));

            // Separate with a period
            token.append(".");

            // Create the JWT Claims Object
            String[] claimArray = new String[4];
            claimArray[0] = "3MVG9oFjVm9kgtphcLA2.aoqcF0PAv0Od8XYradCx583utE2Tx.WTDXyyZOpUkXuS4mooX4KiNqchq4WUNJNE";
            claimArray[1] = "oar_system@nist.gov.devpro";
            claimArray[2] = "https://test.salesforce.com";
            claimArray[3] = Long.toString((System.currentTimeMillis() / 1000) + 300);
            MessageFormat claims;
            claims = new MessageFormat(claimTemplate);
            String payload = claims.format(claimArray);

            // Add the encoded claims object
            token.append(Base64.encodeBase64URLSafeString(payload.getBytes("UTF-8")));

            // Load the private key from a keystore
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(Files.newInputStream(Paths.get("/Users/one1/oarkeystore.p12")), "oarkeystore".toCharArray());
            PrivateKey privateKey = (PrivateKey) keystore.getKey("oarcertalias", "omar".toCharArray());

            // Sign the JWT Header + "." + JWT Claims Object
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(token.toString().getBytes("UTF-8"));
            String signedPayload = Base64.encodeBase64URLSafeString(signature.sign());

            // Separate with a period
            token.append(".");

            // Add the encoded signature
            token.append(signedPayload);

            return token.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}