package com.helloagents.demo.travel.model;

public record BudgetDetail(
        int tickets,
        int hotel,
        int meals,
        int transport,
        int total
) {}