package com.helloagents.demo.travel.model;

public record Attraction(
        String name,
        String description,
        double latitude,
        double longitude,
        String openTime,
        String ticketPrice,
        String imageUrl
) {}