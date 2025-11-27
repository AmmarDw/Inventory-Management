package com.speedit.inventorysystem.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
// TODO, get red of it
@Configuration // Or add this method to your @SpringBootApplication class
public class JacksonConfig { // Or use your main application class name

    @Bean
    public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
        return new Jackson2ObjectMapperBuilder()
                .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .postConfigurer(objectMapper -> {
                    // This mixin helps Jackson handle Hibernate proxies and ignore @JsonIgnore
                    // It's a more robust way to apply the ignoring rule.
                    objectMapper.addMixIn(Object.class, HibernateAwareJsonMixIn.class);
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static abstract class HibernateAwareJsonMixIn {
        // This is an empty mixin. Its purpose is to be applied to all classes (Object.class)
        // to activate the @JsonIgnoreProperties annotation globally for Jackson serialization
        // within this ObjectMapper's context.
        // ignoreUnknown = true helps with Hibernate proxy fields.
    }
}
