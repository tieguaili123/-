package com.mint.health.controller;

import com.mint.health.common.ApiResponse;
import com.mint.health.dto.BodyProfileRequest;
import com.mint.health.service.BodyProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/body-profile")
public class BodyProfileController {

    private final BodyProfileService bodyProfileService;

    public BodyProfileController(BodyProfileService bodyProfileService) {
        this.bodyProfileService = bodyProfileService;
    }

    @PostMapping("/save")
    public ApiResponse<?> save(@RequestBody BodyProfileRequest request) {
        return ApiResponse.success(bodyProfileService.save(request));
    }

    @GetMapping("/detail")
    public ApiResponse<?> detail(@RequestParam Long userId) {
        return ApiResponse.success(bodyProfileService.detail(userId));
    }
}
