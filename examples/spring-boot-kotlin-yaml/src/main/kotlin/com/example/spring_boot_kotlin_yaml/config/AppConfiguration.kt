package com.example.spring_boot_kotlin_yaml.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KoogConfiguration::class)
class AppConfiguration