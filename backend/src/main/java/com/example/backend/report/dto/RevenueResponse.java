package com.example.backend.report.dto;

import java.util.List;

public class RevenueResponse {
    private final String granularity;
    private final List<RevenuePoint> points;

    public RevenueResponse(String granularity, List<RevenuePoint> points) {
        this.granularity = granularity;
        this.points = points;
    }

    public String getGranularity() { return granularity; }
    public List<RevenuePoint> getPoints() { return points; }
}
