package com.helloagents.demo.travel.model;

import java.util.List;

public record DayItinerary(
        String date,
        String dayLabel,
        List<Attraction> attractions,
        List<String> meals,
        Hotel hotel,
        String weather
) {}