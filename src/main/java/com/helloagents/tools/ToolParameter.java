package com.helloagents.tools;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Holds all parameter definitions for a single {@link Tool}.
 *
 * <p>Each individual parameter is described by a {@link Param} record.
 * Use the factory methods to build instances:
 * <pre>
 *   ToolParameter.of(
 *       Param.required("expression", "Math expression to evaluate", "string"),
 *       Param.optional("precision", "Decimal places in the result", "number")
 *   )
 * </pre>
 */
public final class ToolParameter {

    /**
     * Describes a single parameter accepted by a tool.
     *
     * @param name        parameter name
     * @param description what the parameter represents
     * @param type        expected value type, e.g. {@code "string"}, {@code "number"}
     * @param required    whether the parameter is mandatory
     */
    public record Param(String name, String description, String type, boolean required) {

        /** Creates a required parameter. */
        public static Param required(String name, String description, String type) {
            return new Param(name, description, type, true);
        }

        /** Creates an optional parameter. */
        public static Param optional(String name, String description, String type) {
            return new Param(name, description, type, false);
        }

        /** Returns a formatted line suitable for inclusion in a system prompt. */
        public String format() {
            return "  - %s (%s%s): %s".formatted(
                    name, type,
                    required ? ", required" : ", optional",
                    description);
        }
    }

    private static final ToolParameter EMPTY = new ToolParameter(List.of());

    private final List<Param> params;

    private ToolParameter(List<Param> params) {
        this.params = params;
    }

    /** Returns a {@code ToolParameter} containing no parameters. */
    public static ToolParameter empty() {
        return EMPTY;
    }

    /** Creates a {@code ToolParameter} from the given parameter definitions. */
    public static ToolParameter of(Param... params) {
        return new ToolParameter(List.copyOf(Arrays.asList(params)));
    }

    /** Returns {@code true} if no parameters are defined. */
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /** Returns the ordered list of parameter definitions. */
    public List<Param> list() {
        return params;
    }

    /** Returns a formatted multi-line description of all parameters for system-prompt injection. */
    public String describe() {
        return params.stream()
                .map(Param::format)
                .collect(Collectors.joining("\n"));
    }
}
