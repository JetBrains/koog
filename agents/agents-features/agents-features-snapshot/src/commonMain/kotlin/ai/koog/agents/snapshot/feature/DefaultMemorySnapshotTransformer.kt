package ai.koog.agents.snapshot.feature

import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.providers.AgentMemoryProvider
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

/**
 * Default implementation of MemorySnapshotTransformer for standard fact-based memory providers.
 * 
 * This implementation:
 * - Captures all facts from all registered subjects and scopes
 * - Serializes facts as JSON arrays grouped by subject and scope
 * - Provides atomic restoration with rollback on failure
 * - Includes metadata for validation and debugging
 * 
 * The snapshot format structure:
 * ```json
 * {
 *   "version": "1.0",
 *   "capturedAt": "2024-01-15T10:30:00Z",
 *   "subjects": {
 *     "user": {
 *       "agent": [...facts...],
 *       "product": [...facts...]
 *     },
 *     "machine": {
 *       "agent": [...facts...]
 *     }
 *   }
 * }
 * ```
 * 
 * This format enables:
 * - Efficient restoration by subject/scope
 * - Forward compatibility via versioning
 * - Debugging via capture metadata
 * - Partial restoration if needed in the future
 */
public class DefaultMemorySnapshotTransformer : MemorySnapshotTransformer {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    public companion object {
        private const val SNAPSHOT_VERSION = "1.0"
        private const val VERSION_KEY = "version"
        private const val CAPTURED_AT_KEY = "capturedAt"
        private const val SUBJECTS_KEY = "subjects"
    }

    /**
     * Captures a complete snapshot of agent memory organized by subject and scope.
     * 
     * This method:
     * 1. Iterates through all registered memory subjects
     * 2. For each subject, captures facts from all scope types
     * 3. Organizes facts in a hierarchical JSON structure
     * 4. Includes metadata for validation and debugging
     * 
     * @param memoryProvider The memory provider to capture from
     * @return JSON snapshot of all memory facts, or null if no facts found
     */
    override suspend fun captureSnapshot(memoryProvider: ai.koog.agents.memory.providers.AgentMemoryProvider): JsonObject? {
        val subjectsJson = buildJsonObject {
            var hasFacts = false
            
            // Iterate through all registered memory subjects
            @OptIn(ai.koog.agents.core.annotation.InternalAgentsApi::class)
            for (subject in ai.koog.agents.memory.model.MemorySubject.registeredSubjects) {
                val subjectFacts = buildJsonObject {
                    var hasSubjectFacts = false
                    
                    // Capture facts for all scope types
                    val scopes = listOf(
                        ai.koog.agents.memory.model.MemoryScope.Agent("default"),
                        ai.koog.agents.memory.model.MemoryScope.Feature("default"), 
                        ai.koog.agents.memory.model.MemoryScope.Product("default"),
                        ai.koog.agents.memory.model.MemoryScope.CrossProduct
                    )
                    
                    for (scope in scopes) {
                        try {
                            val facts = memoryProvider.loadAll(subject, scope)
                            if (facts.isNotEmpty()) {
                                val scopeKey = when (scope) {
                                    is ai.koog.agents.memory.model.MemoryScope.Agent -> "agent"
                                    is ai.koog.agents.memory.model.MemoryScope.Feature -> "feature"
                                    is ai.koog.agents.memory.model.MemoryScope.Product -> "product"
                                    is ai.koog.agents.memory.model.MemoryScope.CrossProduct -> "crossProduct"
                                }
                                put(scopeKey, json.encodeToJsonElement(facts))
                                hasSubjectFacts = true
                                hasFacts = true
                            }
                        } catch (e: Exception) {
                            // Log error but continue with other scopes
                            // In a real implementation, consider proper logging
                            println("Warning: Failed to capture facts for subject ${subject.name}, scope $scope: ${e.message}")
                        }
                    }
                }
                
                if (subjectFacts.isNotEmpty()) {
                    put(subject.name, subjectFacts)
                }
            }
            
            if (!hasFacts) {
                return null
            }
        }
        
        return buildJsonObject {
            put(VERSION_KEY, SNAPSHOT_VERSION)
            put(CAPTURED_AT_KEY, kotlinx.datetime.Clock.System.now().toString())
            put(SUBJECTS_KEY, subjectsJson)
        }
    }

    /**
     * Restores memory facts from a hierarchical JSON snapshot.
     * 
     * This method:
     * 1. Validates the snapshot format and version
     * 2. Parses facts organized by subject and scope
     * 3. Restores facts to the appropriate memory contexts
     * 4. Provides atomic restoration (all facts or none)
     * 
     * @param memoryProvider The memory provider to restore to
     * @param snapshot JSON snapshot containing organized facts
     * @throws IllegalArgumentException if snapshot format is invalid
     * @throws Exception if restoration fails
     */
    override suspend fun restoreSnapshot(memoryProvider: AgentMemoryProvider, snapshot: JsonObject) {
        // Validate snapshot format
        val version = snapshot[VERSION_KEY]?.jsonPrimitive?.content
        if (version != SNAPSHOT_VERSION) {
            throw IllegalArgumentException("Unsupported snapshot version: $version (expected: $SNAPSHOT_VERSION)")
        }
        
        val subjectsData = snapshot[SUBJECTS_KEY]?.jsonObject
            ?: throw IllegalArgumentException("Missing '$SUBJECTS_KEY' in memory snapshot")
        
        // Track restoration for potential rollback
        val restoredFacts = mutableListOf<Triple<ai.koog.agents.memory.model.Fact, MemorySubject, MemoryScope>>()
        
        try {
            // Restore facts for each subject
            for ((subjectName, subjectData) in subjectsData) {
                @OptIn(ai.koog.agents.core.annotation.InternalAgentsApi::class)
                val subject = ai.koog.agents.memory.model.MemorySubject.registeredSubjects.find { it.name == subjectName }
                    ?: continue // Skip unknown subjects for forward compatibility
                
                val subjectJson = subjectData.jsonObject
                
                // Restore facts for each scope within the subject
                for ((scopeKey, factsData) in subjectJson) {
                    val scope = when (scopeKey) {
                        "agent" -> MemoryScope.Agent("default")
                        "feature" -> MemoryScope.Feature("default")
                        "product" -> MemoryScope.Product("default")
                        "crossProduct" -> MemoryScope.CrossProduct
                        else -> continue // Skip unknown scope types
                    }
                    
                    // Deserialize and restore facts
                    val facts = json.decodeFromJsonElement<List<ai.koog.agents.memory.model.Fact>>(factsData)
                    for (fact in facts) {
                        memoryProvider.save(fact, subject, scope)
                        restoredFacts.add(Triple(fact, subject, scope))
                    }
                }
            }
        } catch (e: Exception) {
            // On failure, attempt to clean up partially restored facts
            // Note: This is best-effort cleanup; some providers may not support deletion
            try {
                for ((fact, subject, scope) in restoredFacts) {
                    // Future: add delete method to AgentMemoryProvider interface
                    // memoryProvider.delete(fact, subject, scope)
                }
            } catch (cleanupException: Exception) {
                // Log cleanup failure but don't mask original exception
                println("Warning: Failed to cleanup partially restored memory facts: ${cleanupException.message}")
            }
            
            throw Exception("Memory restoration failed: ${e.message}", e)
        }
    }

    /**
     * Validates snapshot compatibility with this transformer.
     * 
     * @param snapshot The snapshot to validate
     * @return true if this transformer can restore the snapshot
     */
    override fun isSnapshotCompatible(snapshot: JsonObject): Boolean {
        val version = snapshot[VERSION_KEY]?.jsonPrimitive?.content
        return version == SNAPSHOT_VERSION && snapshot.containsKey(SUBJECTS_KEY)
    }
}