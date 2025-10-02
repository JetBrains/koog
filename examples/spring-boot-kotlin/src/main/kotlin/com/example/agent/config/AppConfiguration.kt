package com.example.agent.config

import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import com.example.agent.service.S3StorageProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
@EnableConfigurationProperties(AgentConfiguration::class)
class AppConfiguration(val agentConfiguration: AgentConfiguration) {

    @Bean
    @ConditionalOnProperty(prefix = "agent.s3_persistence", name = ["enabled"], havingValue = "true")
    fun s3Persistence(): PersistenceStorageProvider {
        val persistence = agentConfiguration.s3Persistence!!

        return S3StorageProvider(persistence.region, persistence.bucket, persistence.path)
    }
}
