package com.stecker.backend.controller;

import com.stecker.backend.dto.TodayRecommendations;
import com.stecker.backend.service.TodayRecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Today's recommendations for guests (public). */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final TodayRecommendationService service;

    public RecommendationController(TodayRecommendationService service) {
        this.service = service;
    }

    /** Recommendations for today in Bremen; an empty list when there are none. */
    @GetMapping("/today")
    public TodayRecommendations today() {
        return service.today();
    }
}
