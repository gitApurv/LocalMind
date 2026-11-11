package com.localmind.tools.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

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
            return format(new ExpressionBuilder(expression.trim()).build().evaluate());
        } catch (ArithmeticException e) {
            return "Error: Division by zero: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "Error: Invalid mathematical syntax: " + e.getMessage();
        } catch (Exception e) {
            return "Error: the expression could not be evaluated.";
        }
    }

    /** Whole numbers are rendered without a fractional part. */
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
