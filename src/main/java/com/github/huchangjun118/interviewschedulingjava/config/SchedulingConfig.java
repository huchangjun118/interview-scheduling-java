package com.github.huchangjun118.interviewschedulingjava.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SchedulingProperties.class)
public class SchedulingConfig {
}
