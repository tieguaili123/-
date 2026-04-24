package com.mint.health.service;

import com.mint.health.dto.BodyProfileRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BodyProfileService {

    private final JdbcTemplate jdbcTemplate;

    public BodyProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> save(BodyProfileRequest request) {
        validate(request);

        double bmi = calcBmi(request.getHeight(), request.getCurrentWeight());
        double bmr = calcBmr(request.getGender(), request.getAge(), request.getHeight(), request.getCurrentWeight());
        double recommendCalories = calcRecommendCalories(bmr, request.getActivityLevel());

        jdbcTemplate.update("update user set gender = ?, age = ?, update_time = now() where id = ?",
                request.getGender(), request.getAge(), request.getUserId());

        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from user_body_profile where user_id = ?",
                Integer.class,
                request.getUserId()
        );

        if (count != null && count > 0) {
            jdbcTemplate.update("update user_body_profile set height=?, current_weight=?, target_weight=?, gender=?, age=?, activity_level=?, bmi=?, bmr=?, recommended_calories=?, update_time=now() where user_id=?",
                    request.getHeight(), request.getCurrentWeight(), request.getTargetWeight(), request.getGender(), request.getAge(), request.getActivityLevel(), bmi, bmr, recommendCalories, request.getUserId());
        } else {
            jdbcTemplate.update("insert into user_body_profile(user_id, height, current_weight, target_weight, gender, age, activity_level, bmi, bmr, recommended_calories, update_time) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                    request.getUserId(), request.getHeight(), request.getCurrentWeight(), request.getTargetWeight(), request.getGender(), request.getAge(), request.getActivityLevel(), bmi, bmr, recommendCalories);
        }

        return detail(request.getUserId());
    }

    public Map<String, Object> detail(Long userId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "select * from user_body_profile where user_id = ?",
                userId
        );
        if (!list.isEmpty()) {
            return list.get(0);
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "select id as user_id, gender, age from user where id = ?",
                userId
        );
        if (users.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>(users.get(0));
        result.put("height", null);
        result.put("current_weight", null);
        result.put("target_weight", null);
        result.put("activity_level", "LOW");
        result.put("bmi", 0);
        result.put("bmr", 0);
        result.put("recommended_calories", 0);
        return result;
    }

    private void validate(BodyProfileRequest request) {
        if (request.getUserId() == null) {
            throw new RuntimeException("用户不存在");
        }
        if (request.getHeight() == null || request.getHeight() <= 0) {
            throw new RuntimeException("请输入正确的身高");
        }
        if (request.getCurrentWeight() == null || request.getCurrentWeight() <= 0) {
            throw new RuntimeException("请输入正确的当前体重");
        }
        if (request.getTargetWeight() == null || request.getTargetWeight() <= 0) {
            throw new RuntimeException("请输入正确的目标体重");
        }
        if (request.getAge() == null || request.getAge() <= 0) {
            throw new RuntimeException("请输入正确的年龄");
        }
        if (request.getGender() == null) {
            throw new RuntimeException("请选择性别");
        }
        if (request.getActivityLevel() == null || request.getActivityLevel().trim().isEmpty()) {
            throw new RuntimeException("请选择活动水平");
        }
    }

    private double calcBmi(Double height, Double weight) {
        double meter = height / 100.0;
        return Math.round(weight / (meter * meter) * 100.0) / 100.0;
    }

    private double calcBmr(Integer gender, Integer age, Double height, Double weight) {
        double value;
        if (gender != null && gender == 2) {
            value = 10 * weight + 6.25 * height - 5 * age - 161;
        } else {
            value = 10 * weight + 6.25 * height - 5 * age + 5;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private double calcRecommendCalories(double bmr, String activityLevel) {
        double factor = 1.2;
        if ("MEDIUM".equalsIgnoreCase(activityLevel)) {
            factor = 1.55;
        } else if ("HIGH".equalsIgnoreCase(activityLevel)) {
            factor = 1.9;
        }
        return Math.round(bmr * factor * 100.0) / 100.0;
    }
}
