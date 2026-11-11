package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateTimeToolsTest {

    private DateTimeTools dateTimeTools;

    @BeforeEach
    void setUp() {
        dateTimeTools = new DateTimeTools();
    }

    @Test
    @DisplayName("getCurrentDateTime() should return ISO-8601 string, day of week, and timezone")
    void getCurrentDateTimeSuccess() {
        String result = dateTimeTools.getCurrentDateTime();
        assertNotNull(result);
        assertTrue(result.startsWith("Current Date & Time:"));
        assertTrue(result.contains("Timezone:"));
        assertTrue(result.contains(ZonedDateTime.now().getDayOfWeek().toString()));
    }

    @Test
    @DisplayName("FunctionCallback for DateTimeTools should execute with empty request payload")
    void functionCallbackExecution() {
        var callback = dateTimeTools.toolCallback();
        assertNotNull(callback);
        assertEquals("getCurrentDateTime", callback.getToolDefinition().name());

        String jsonResult = callback.call("{}");
        assertNotNull(jsonResult);
        assertTrue(jsonResult.contains("Current Date & Time:"));
    }
}
