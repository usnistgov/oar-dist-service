package gov.nist.oar.distrib.service.rpa.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import gov.nist.oar.distrib.service.rpa.utils.JsonSerializable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that encapsulates the Google service response for the reCaptcha validation request.
 * Success property with a true value means the user has been validated.
 * Otherwise, the user was not validated and the errorCodes property will contain the reason.
 * <p>
 * The hostname property refers to the host that redirected the user to the reCAPTCHA.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
        "success",
        "challenge_ts",
        "hostname",
        "error-codes"
})
public class RecaptchaResponse extends JsonSerializable {
    @JsonProperty("success")
    boolean success;
    @JsonProperty("challenge_ts")
    String challengeTs;
    @JsonProperty("hostname")
    String hostname;
    @JsonProperty("error-codes")
    private ErrorCode[] errorCodes;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getChallengeTs() {
        return challengeTs;
    }

    public void setChallengeTs(String challengeTs) {
        this.challengeTs = challengeTs;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public ErrorCode[] getErrorCodes() {
        return errorCodes;
    }

    public String[] getErrorCodesAsStrings() {
        ErrorCode[] codes = getErrorCodes();
        if (codes == null) {
            return new String[0];
        }
        return Arrays.stream(codes)
                .map(ErrorCode::name)
                .toArray(String[]::new);
    }

    public void setErrorCodes(ErrorCode[] errorCodes) {
        this.errorCodes = errorCodes;
    }

    @JsonIgnore
    public boolean hasClientError() {
        ErrorCode[] errors = getErrorCodes();
        if (errors == null) {
            return false;
        }
        for (ErrorCode error : errors) {
            switch (error) {
                case InvalidResponse:
                case MissingResponse:
                    return true;
            }
        }
        return false;
    }

    public enum ErrorCode {
        MissingSecret, InvalidSecret,
        MissingResponse, InvalidResponse;

        private static Map<String, ErrorCode> errorsMap = new HashMap<>(4);

        static {
            errorsMap.put("missing-input-secret", MissingSecret);
            errorsMap.put("invalid-input-secret", InvalidSecret);
            errorsMap.put("missing-input-response", MissingResponse);
            errorsMap.put("invalid-input-response", InvalidResponse);
        }

        @JsonCreator
        public static ErrorCode forValue(String value) {
            return errorsMap.get(value.toLowerCase());
        }
    }

}
