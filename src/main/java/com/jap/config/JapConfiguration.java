package com.jap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(JapProperties.class)
public class JapConfiguration {
}
