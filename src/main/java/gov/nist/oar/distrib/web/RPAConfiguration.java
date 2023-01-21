package gov.nist.oar.distrib.web;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


public class RPAConfiguration {

    private String salesforceInstanceUrl = null;

    private String pdrCachingUrl = null;

    public String getSalesforceInstanceUrl() {
        return salesforceInstanceUrl;
    }

    public void setSalesforceInstanceUrl(String salesforceInstanceUrl) {
        this.salesforceInstanceUrl = salesforceInstanceUrl;
    }

    public String getPdrCachingUrl() {
        return pdrCachingUrl;
    }

    public void setPdrCachingUrl(String pdrCachingUrl) {
        this.pdrCachingUrl = pdrCachingUrl;
    }
}