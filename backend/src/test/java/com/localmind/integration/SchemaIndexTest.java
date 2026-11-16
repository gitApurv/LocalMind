package com.localmind.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The indexes declared on the entities exist in the generated schema, with their columns in order. Every list query
 * filters by owner or conversation and sorts by a timestamp, so a missing index means a full table scan.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaIndexTest {

    @Autowired
    private DataSource dataSource;

    @ParameterizedTest(name = "{0}.{1}")
    @CsvSource(delimiter = '|', value = {"conversations | idx_conversations_user_updated | user_id,updated_at",
            "messages | idx_messages_conversation_created | conversation_id,created_at",
            "attachments | idx_attachments_conversation_created | conversation_id,created_at",
            "user_memories | idx_user_memories_user_created | user_id,created_at",
            "refresh_tokens | idx_refresh_tokens_user | user_id",
            "refresh_tokens | idx_refresh_tokens_expires | expires_at"})
    void indexExistsWithColumnsInOrder(String table, String index, String columns) throws SQLException {
        assertThat(indexColumns(table)).containsEntry(index, List.of(columns.split(",")));
    }

    /** Index name to its columns in key order, read from JDBC metadata so the check does not depend on the dialect. */
    private Map<String, List<String>> indexColumns(String table) throws SQLException {
        Map<String, Map<Short, String>> byIndex = new TreeMap<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false,
                        false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (name == null || column == null || rows.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                byIndex.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new TreeMap<>())
                        .put(rows.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
            }
        }
        Map<String, List<String>> result = new TreeMap<>();
        byIndex.forEach((name, columns) -> result.put(name, new ArrayList<>(columns.values())));
        return result;
    }
}
