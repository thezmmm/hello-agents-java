package com.helloagents.tools;


/**
 * Simple calculator tool — evaluates a mathematical expression string.
 *
 * <p>Example: {@code calculate[2 + 2 * 3]} → {@code 8.0}
 */
public class CalculatorTool implements Tool {

    @Override
    public String name() {
        return "calculate";
    }

    @Override
    public String description() {
        return "Evaluate a mathematical expression. Input: a math expression string, e.g. (3 + 5) * 2";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            ToolParameter.Param.required("expression", "Mathematical expression to evaluate, e.g. (3 + 5) * 2", "string")
        );
    }

    @Override
    public String execute(String input) {
        // Use Nashorn (Java 8-14) or Rhino fallback. For Java 17, use a simple parser.
        try {
            double result = evalSimple(input.strip());
            // Return integer if no fractional part
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "Error: could not evaluate expression: " + input;
        }
    }

    /**
     * Very small recursive descent parser for +, -, *, /, ^ and parentheses.
     * Safe alternative to ScriptEngine / eval.
     */
    private double evalSimple(String expr) {
        return new ExprParser(expr).parse();
    }

    private static class ExprParser {
        private final String input;
        private int pos;

        ExprParser(String input) {
            this.input = input.replaceAll("\\s+", "");
            this.pos = 0;
        }

        double parse() {
            double result = parseAddSub();
            if (pos != input.length()) {
                throw new IllegalArgumentException("Unexpected character at position " + pos);
            }
            return result;
        }

        private double parseAddSub() {
            double left = parseMulDiv();
            while (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                char op = input.charAt(pos++);
                double right = parseMulDiv();
                left = op == '+' ? left + right : left - right;
            }
            return left;
        }

        private double parseMulDiv() {
            double left = parsePower();
            while (pos < input.length() && (input.charAt(pos) == '*' || input.charAt(pos) == '/')) {
                char op = input.charAt(pos++);
                double right = parsePower();
                left = op == '*' ? left * right : left / right;
            }
            return left;
        }

        private double parsePower() {
            double base = parseUnary();
            if (pos < input.length() && input.charAt(pos) == '^') {
                pos++;
                double exp = parseUnary();
                return Math.pow(base, exp);
            }
            return base;
        }

        private double parseUnary() {
            if (pos < input.length() && input.charAt(pos) == '-') {
                pos++;
                return -parsePrimary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++; // consume '('
                double val = parseAddSub();
                if (pos >= input.length() || input.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                pos++; // consume ')'
                return val;
            }
            // Parse number
            int start = pos;
            if (pos < input.length() && input.charAt(pos) == '-') pos++;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("Expected number at position " + pos);
            return Double.parseDouble(input.substring(start, pos));
        }
    }
}
