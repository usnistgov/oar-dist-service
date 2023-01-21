package gov.nist.oar.distrib.web;

import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Value
@NonFinal
@Validated
@ConfigurationProperties("distrib.rpa")
public class RPAConfiguration {

    private String salesforceInstanceUrl = null;
    private String pdrCachingUrl = null;
}