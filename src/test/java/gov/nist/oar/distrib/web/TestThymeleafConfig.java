package gov.nist.oar.distrib.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.thymeleaf.spring6.ISpringTemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

@Configuration
public class TestThymeleafConfig {

    /**
     * Sets up a template resolver for Thymeleaf to use. This is a replacement for
     * the
     * normal resolver that Spring Boot uses, which looks for templates in the
     * src/main
     * directory. Instead, this resolver looks in the classpath, which is better for
     * testing.
     * <p>
     * This resolver is marked as primary so that it takes precedence over the
     * default
     * resolver.
     */
    @Bean
    @Primary
    public SpringResourceTemplateResolver templateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        return resolver;
    }

    /**
     * Configures and returns a Thymeleaf template engine.
     * <p>
     * This engine uses the provided template resolver for resolving templates and
     * supports additional customizations if needed. It is configured to handle
     * HTML templates with UTF-8 encoding.
     * 
     * @param resolver the template resolver for resolving templates
     * @return a configured instance of ISpringTemplateEngine
     */

    @Bean
    public ISpringTemplateEngine templateEngine(SpringResourceTemplateResolver resolver) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /**
     * Sets up a Thymeleaf view resolver that will use the ISpringTemplateEngine to
     * render templates. The view resolver is configured to render UTF-8 encoded
     * templates and has an order of 1. This means that it will be the first view
     * resolver looked at when resolving views.
     * 
     * @param engine the template engine to use for rendering templates
     * @return a configured instance of ThymeleafViewResolver
     */
    @Bean
    public ThymeleafViewResolver viewResolver(ISpringTemplateEngine engine) {
        ThymeleafViewResolver vr = new ThymeleafViewResolver();
        vr.setTemplateEngine(engine);
        vr.setCharacterEncoding("UTF-8");
        vr.setOrder(1);
        return vr;
    }
}
