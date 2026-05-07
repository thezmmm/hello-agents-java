package com.helloagents.demo.travel.model;

import java.util.List;

public record TravelPlan(
        String destination,
        String startDate,
        String endDate,
        List<DayItinerary> days,
        BudgetDetail budget,
        String weatherSummary,
        List<String> tips
) {}