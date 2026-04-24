package com.mint.health.controller;

import com.mint.health.common.ApiResponse;
import com.mint.health.dto.LoginRequest;
import com.mint.health.dto.RegisterRequest;
import com.mint.health.dto.ResetPasswordRequest;
import com.mint.health.dto.UpdateUserRequest;
import com.mint.health.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest request) {
        log.info("用户注册请求 username={}, email={}", request.getUsername(), request.getEmail());
        return ApiResponse.success(userService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        log.info("用户登录请求 username={}", request.getUsername());
        return ApiResponse.success(userService.login(request.getUsername(), request.getPassword()));
    }

    @GetMapping("/info")
    public ApiResponse<?> info(@RequestParam Long userId) {
        log.info("查询用户信息 userId={}", userId);
        return ApiResponse.success(userService.getUserInfo(userId));
    }

    @PostMapping("/update")
    public ApiResponse<?> update(@RequestBody UpdateUserRequest request) {
        log.info("更新用户信息 userId={}, verifyType={}", request.getUserId(), request.getVerifyType());
        return ApiResponse.success(userService.updateUser(request));
    }

    @GetMapping("/send-email-code")
    public ApiResponse<?> sendEmailCode(@RequestParam String email, @RequestParam String scene) {
        log.info("发送邮箱验证码 email={}, scene={}", email, scene);
        return ApiResponse.success(userService.sendEmailCode(email, scene));
    }

    @PostMapping("/reset-password")
    public ApiResponse<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        log.info("找回密码 username={}, email={}", request.getUsername(), request.getEmail());
        userService.resetPassword(request.getUsername(), request.getEmail(), request.getVerificationCode(), request.getNewPassword());
        return ApiResponse.success("密码重置成功", null);
    }
}
