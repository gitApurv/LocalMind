package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * LLM tool {@code calculateExpression}: lets the model evaluate a mathematical expression (via exp4j) instead of doing
 * the arithmetic itself. Invalid input and division by zero come back as readable error strings.
 */
@Slf4j
@Component
public class MathTools {

    private static final String NAME = "calculateExpression";
    private static final String DESCRIPTION = "Evaluate a mathematical expression. Supports +, -, *, /, ^, %, parentheses, "
            + "and standard math functions like sqrt, sin, cos, abs.";

    public record MathRequest(@JsonProperty(value = "expression", required = true) String expression) {
    }

    public String calculateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return "Error: Expression cannot be null or blank.";
        }
        try {
            double result = new ExpressionBuilder(expression.trim()).build().evaluate();
            return format(result);
        } catch (ArithmeticException e) {
            log.debug("Arithmetic error in expression '{}': {}", expression, e.getMessage());
            return "Error: Division by zero.";
        } catch (IllegalArgumentException e) {
            log.debug("Invalid expression '{}': {}", expression, e.getMessage());
            return "Error: Invalid mathematical syntax.";
        } catch (Exception e) {
            log.debug("Expression '{}' could not be evaluated", expression, e);
            return "Error: the expression could not be evaluated.";
        }
    }

    private static String format(double result) {
        if (Double.isNaN(result)) {
            return "Error: Mathematical result is NaN (Not a Number).";
        }
        if (Double.isInfinite(result)) {
            return "Error: Division by zero or infinite result.";
        }
        if (result == Math.floor(result) && result <= Long.MAX_VALUE && result >= Long.MIN_VALUE) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback
                .builder(NAME,
                        (MathRequest request) -> calculateExpression(request != null ? request.expression() : null))
                .description(DESCRIPTION).inputType(MathRequest.class).build();
    }
}
