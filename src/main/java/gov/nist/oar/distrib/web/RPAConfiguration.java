package gov.nist.oar.distrib.web;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Value
@NonFinal
@Validated
@ConfigurationProperties("distrib.rpa")
public class RPAConfiguration {

    @Getter
    @Setter
    private String salesforceInstanceUrl = null;
    @Getter
    @Setter
    private String pdrCachingUrl = null;
}