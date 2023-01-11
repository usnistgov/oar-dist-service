package gov.nist.oar.distrib.web.salesforce;

import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Value
@NonFinal
@Validated
@ConfigurationProperties(prefix = "salesforce")
public class Configuration {
}
