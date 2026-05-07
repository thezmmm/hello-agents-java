package com.helloagents.demo.travel.model;

public record Hotel(
        String name,
        String address,
        double latitude,
        double longitude,
        String pricePerNight,
        double rating,
        String type
) {}