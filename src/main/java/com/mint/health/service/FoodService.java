package com.mint.health.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FoodService {

    private final JdbcTemplate jdbcTemplate;

    public FoodService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> search(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return jdbcTemplate.queryForList(
                "select id, food_name, category, calories_per_100g, protein_per_100g, fat_per_100g, carbohydrate_per_100g, image_url from food where food_name like ? order by id desc limit 50",
                "%" + value + "%"
        );
    }

    public Map<String, Object> detail(Long id) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "select * from food where id = ?",
                id
        );
        if (list.isEmpty()) {
            throw new RuntimeException("食物不存在");
        }
        return list.get(0);
    }
}
