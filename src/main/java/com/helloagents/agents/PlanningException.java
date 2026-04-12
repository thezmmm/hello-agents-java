package com.helloagents.agents;

/**
 * Thrown when {@link Planner} cannot produce a valid plan from the model's response.
 */
public class PlanningException extends RuntimeException {

    public PlanningException(String message) {
        super(message);
    }
}
