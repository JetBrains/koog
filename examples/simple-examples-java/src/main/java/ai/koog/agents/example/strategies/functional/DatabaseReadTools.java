package ai.koog.agents.example.strategies.functional;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Stub read-only database tools for the functional strategy example.
 * In a real application, these would query an actual database.
 */
public class DatabaseReadTools implements ToolSet {

    @Tool
    @LLMDescription("Query the database and return matching records as a JSON string")
    public String queryRecords(
        @LLMDescription("Name of the database table to query") String tableName,
        @LLMDescription("SQL WHERE clause for filtering results") String whereClause
    ) {
        return "[]";
    }

    @Tool
    @LLMDescription("Get a single record by its ID from the specified table")
    public String getRecordById(
        @LLMDescription("Name of the database table") String tableName,
        @LLMDescription("Record identifier") String id
    ) {
        return "Record not found";
    }

    @Tool
    @LLMDescription("Count the number of records matching the given criteria")
    public int countRecords(
        @LLMDescription("Name of the database table") String tableName,
        @LLMDescription("SQL WHERE clause for filtering") String whereClause
    ) {
        return 0;
    }
}
