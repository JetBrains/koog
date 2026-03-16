package ai.koog.agents.example.strategies.functional;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Stub database write tools for the functional strategy example.
 * In a real application, these would modify records in an actual database.
 */
public class DatabaseWriteTools implements ToolSet {

    @Tool
    @LLMDescription("Insert a new record into the specified table")
    public String insertRecord(
        @LLMDescription("Name of the database table") String tableName,
        @LLMDescription("JSON string representing the record data") String recordJson
    ) {
        return "Record inserted into " + tableName;
    }

    @Tool
    @LLMDescription("Update an existing record in the specified table")
    public String updateRecord(
        @LLMDescription("Name of the database table") String tableName,
        @LLMDescription("Record identifier") String id,
        @LLMDescription("JSON string with fields to update") String updateJson
    ) {
        return "Record " + id + " updated in " + tableName;
    }

    @Tool
    @LLMDescription("Delete a record from the specified table by its ID")
    public String deleteRecord(
        @LLMDescription("Name of the database table") String tableName,
        @LLMDescription("Record identifier") String id
    ) {
        return "Record " + id + " deleted from " + tableName;
    }
}
