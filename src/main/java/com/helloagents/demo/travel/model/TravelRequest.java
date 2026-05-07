package com.helloagents.demo.travel.model;

public record TravelRequest(
        String destination,
        String startDate,
        String endDate,
        String hotelPreference,
        String preferences
) {}