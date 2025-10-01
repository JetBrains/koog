package com.example.spring_boot_kotlin_yaml.config

import com.example.spring_boot_kotlin_yaml.service.S3StorageProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
@EnableConfigurationProperties(AgentConfiguration::class)
class AppConfiguration(val agentConfiguration: AgentConfiguration) {

    @Bean
    @ConditionalOnProperty(prefix = "agent.s3_persistence", name = ["enabled"], havingValue = "true")
    fun s3Persistency(): PersistencyStorageProvider {
        val persistence = agentConfiguration.s3Persistence!!

        return S3StorageProvider(persistence.region, persistence.bucket, persistence.path)
    }
}