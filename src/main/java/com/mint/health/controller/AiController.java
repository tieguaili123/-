package com.mint.health.controller;

import com.mint.health.common.ApiResponse;
import com.mint.health.dto.AiChatRequest;
import com.mint.health.service.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    @Value("${app.ai.provider:tongyi-qianwen}")
    private String provider;

    @Value("${app.ai.model:qwen-turbo}")
    private String model;

    @Value("${app.ai.vision-model:qwen-vl-max-latest}")
    private String visionModel;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/recognize-food")
    public ApiResponse<?> recognizeFood(@RequestParam Long userId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(aiService.recognizeFood(userId, file));
    }

    @PostMapping("/chat")
    public ApiResponse<?> chat(@RequestBody AiChatRequest request) {
        return ApiResponse.success(aiService.chat(request.getUserId(), request.getQuestion()));
    }

    @GetMapping("/chat/mock-config")
    public ApiResponse<?> mockConfig() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("provider", provider);
        result.put("model", model);
        result.put("visionModel", visionModel);
        result.put("status", "AI问答和拍照识别均已接入千问模型");
        return ApiResponse.success(result);
    }
}
