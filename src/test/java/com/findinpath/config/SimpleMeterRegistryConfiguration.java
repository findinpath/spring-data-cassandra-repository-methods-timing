package com.findinpath.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleMeterRegistryConfiguration {

  @Bean
  public SimpleMeterRegistry simpleMeterRegistry() {
    return new SimpleMeterRegistry();
  }
}
