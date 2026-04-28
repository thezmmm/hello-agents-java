package com.helloagents.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private final CalculatorTool calc = new CalculatorTool();

    @Test
    void addition() {
        assertEquals("5", calc.execute(Map.of("expression", "2 + 3")));
    }

    @Test
    void multiplicationPrecedence() {
        assertEquals("14", calc.execute(Map.of("expression", "2 + 3 * 4")));
    }

    @Test
    void parentheses() {
        assertEquals("20", calc.execute(Map.of("expression", "(2 + 3) * 4")));
    }

    @Test
    void power() {
        assertEquals("8", calc.execute(Map.of("expression", "2^3")));
    }

    @Test
    void decimalResult() {
        assertEquals("2.5", calc.execute(Map.of("expression", "5 / 2")));
    }

    @Test
    void unknownTool() {
        ToolRegistry registry = new ToolRegistry().register(calc);
        String result = registry.execute("unknown", "{}");
        assertTrue(result.startsWith("Error: unknown tool"));
    }
}