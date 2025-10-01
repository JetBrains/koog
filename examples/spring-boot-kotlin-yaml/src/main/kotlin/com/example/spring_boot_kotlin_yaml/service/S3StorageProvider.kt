package com.example.spring_boot_kotlin_yaml.service

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.decodeToString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class S3StorageProvider(
    private val awsRegion: String,
    private val bucketName: String,
    private val path: String
) : PersistencyStorageProvider {

    private val json = Json { prettyPrint = true }


    override suspend fun getCheckpoints(): List<AgentCheckpointData> {
        logger.info { "Getting checkpoints from S3 bucket: $bucketName and path: $path" }

        val request =
            ListObjectsRequest {
                bucket = bucketName
            }

        val s3ObjectKeys = mutableListOf<String>()

        S3Client.fromEnvironment { region = awsRegion }.use { s3 ->
            val response = s3.listObjects(request)
            response.contents?.forEach { s3Object ->
                if (s3Object.key != null) {
                    s3ObjectKeys.add(s3Object.key!!)
                }
            }
        }

        return s3ObjectKeys.mapNotNull { objectKey ->
            try {
                val s3Object = getS3Object(objectKey)
                if (s3Object != null) {
                    json.decodeFromString<AgentCheckpointData>(s3Object)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }


    private suspend fun getS3Object(
        keyName: String,
    ): String? {
        logger.info { "Getting S3 object: $keyName" }

        val request =
            GetObjectRequest {
                key = keyName
                bucket = bucketName
            }

        return S3Client.fromEnvironment { region = awsRegion }.use { s3 ->
            s3.getObject(request) { resp ->
                resp.body?.decodeToString()
            }
        }
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        logger.info { "Saving checkpoint to S3 bucket: $bucketName and path: $path" }

        val serialized = json.encodeToString(AgentCheckpointData.serializer(), agentCheckpointData)

        val metadataVal = mutableMapOf<String, String>()
        metadataVal["myVal"] = "test"

        val request =
            PutObjectRequest {
                bucket = bucketName
                key = path + agentCheckpointData.checkpointId
                metadata = metadataVal
                body = ByteStream.fromString(serialized)
            }

        S3Client.fromEnvironment { region = awsRegion }.use { s3 ->
            s3.putObject(request)
        }
    }

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        return getCheckpoints()
            .maxByOrNull { it.createdAt }
    }
}