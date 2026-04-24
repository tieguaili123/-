package com.mint.health.controller;

import com.mint.health.common.ApiResponse;
import com.mint.health.service.FoodService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/food")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @GetMapping("/search")
    public ApiResponse<?> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(foodService.search(keyword));
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam Long id) {
        return ApiResponse.success(foodService.detail(id));
    }
}
