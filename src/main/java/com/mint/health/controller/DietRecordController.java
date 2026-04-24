package com.mint.health.controller;

import com.mint.health.common.ApiResponse;
import com.mint.health.dto.DietRecordRequest;
import com.mint.health.service.DietRecordService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/diet-record")
public class DietRecordController {

    private final DietRecordService dietRecordService;

    public DietRecordController(DietRecordService dietRecordService) {
        this.dietRecordService = dietRecordService;
    }

    @PostMapping("/add")
    public ApiResponse<?> add(@RequestBody DietRecordRequest request) {
        return ApiResponse.success(dietRecordService.add(request));
    }

    @GetMapping("/today")
    public ApiResponse<?> today(@RequestParam Long userId, @RequestParam String date) {
        return ApiResponse.success(dietRecordService.today(userId, date));
    }

    @GetMapping("/history")
    public ApiResponse<?> history(@RequestParam Long userId) {
        return ApiResponse.success(dietRecordService.history(userId));
    }

    @GetMapping("/trend")
    public ApiResponse<?> trend(@RequestParam Long userId) {
        return ApiResponse.success(dietRecordService.trend(userId));
    }
}
