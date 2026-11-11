package com.localmind.tools.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MathToolsTest {

    private MathTools mathTools;

    @BeforeEach
    void setUp() {
        mathTools = new MathTools();
    }

    @ParameterizedTest(name = "Expression ''{0}'' should evaluate to ''{1}''")
    @CsvSource({"'2 + 2', '4'", "'10 - 4 * 2', '2'", "'(10 - 4) / 2 + 3^2', '12'", "'sqrt(16) + abs(-5)', '9'",
            "'2.5 * 4', '10'", "'100 % 30', '10'"})
    @DisplayName("calculateExpression() should accurately evaluate arithmetic expressions")
    void evaluateValidExpressions(String expression, String expected) {
        String result = mathTools.calculateExpression(expression);
        assertNotNull(result);
        // Normalize double representation if needed (e.g. 4 vs 4.0)
        double expectedVal = Double.parseDouble(expected);
        double actualVal = Double.parseDouble(result);
        assertEquals(expectedVal, actualVal, 0.0001);
    }

    @Test
    @DisplayName("calculateExpression() on division by zero should return friendly error message without throwing")
    void divisionByZeroHandledGracefully() {
        String result = mathTools.calculateExpression("10 / 0");
        assertNotNull(result);
        assertTrue(
                result.toLowerCase().contains("division by zero") || result.toLowerCase().contains("error")
                        || result.toLowerCase().contains("infinity"),
                "Expected error or division by zero message but got: " + result);
    }

    @Test
    @DisplayName("calculateExpression() with invalid syntax should return syntax error message")
    void syntaxErrorHandledGracefully() {
        String result = mathTools.calculateExpression("5 + * 3");
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("error") || result.toLowerCase().contains("invalid"),
                "Expected error message but got: " + result);
    }

    @Test
    @DisplayName("calculateExpression() with blank or null expression should return error")
    void blankExpressionHandledGracefully() {
        String resultNull = mathTools.calculateExpression(null);
        assertTrue(resultNull.toLowerCase().contains("error") || resultNull.toLowerCase().contains("invalid")
                || resultNull.toLowerCase().contains("blank"));

        String resultBlank = mathTools.calculateExpression("   ");
        assertTrue(resultBlank.toLowerCase().contains("error") || resultBlank.toLowerCase().contains("invalid")
                || resultBlank.toLowerCase().contains("blank"));
    }

    @Test
    @DisplayName("FunctionCallback invocation for MathTools should parse JSON argument and return result")
    void functionCallbackExecution() {
        var callback = mathTools.toolCallback();
        assertNotNull(callback);
        assertEquals("calculateExpression", callback.getToolDefinition().name());

        String jsonResult = callback.call("{\"expression\": \"7 * 8\"}");
        assertNotNull(jsonResult);
        assertTrue(jsonResult.contains("56"));
    }
}
