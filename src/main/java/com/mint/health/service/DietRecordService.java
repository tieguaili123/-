package com.mint.health.service;

import com.mint.health.dto.DietRecordRequest;
import com.mint.health.dto.FoodAddItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DietRecordService {

    private final JdbcTemplate jdbcTemplate;

    public DietRecordService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> add(DietRecordRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("请至少添加一个食物");
        }

        jdbcTemplate.update("insert into diet_record(user_id, record_date, meal_type, source_type, total_calories, total_protein, total_fat, total_carbohydrate, update_time) values(?, ?, ?, ?, 0, 0, 0, 0, now())",
                request.getUserId(), request.getRecordDate(), request.getMealType(), request.getSourceType() == null ? "MANUAL" : request.getSourceType());

        Long recordId = jdbcTemplate.queryForObject("select max(id) from diet_record where user_id = ?", Long.class, request.getUserId());

        double totalCalories = 0;
        double totalProtein = 0;
        double totalFat = 0;
        double totalCarbohydrate = 0;

        for (FoodAddItem item : request.getItems()) {
            Map<String, Object> food;
            if (item.getFoodId() != null) {
                food = jdbcTemplate.queryForMap("select * from food where id = ?", item.getFoodId());
            } else {
                food = jdbcTemplate.queryForMap("select * from food where food_name = ? limit 1", item.getFoodName());
            }

            double weight = item.getWeight() == null ? 0D : item.getWeight();
            double calories = calc(weight, food.get("calories_per_100g"));
            double protein = calc(weight, food.get("protein_per_100g"));
            double fat = calc(weight, food.get("fat_per_100g"));
            double carbohydrate = calc(weight, food.get("carbohydrate_per_100g"));

            jdbcTemplate.update("insert into diet_record_item(record_id, food_id, food_name, weight, calories, protein, fat, carbohydrate, source_type, update_time) values(?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                    recordId, food.get("id"), food.get("food_name"), weight, calories, protein, fat, carbohydrate, request.getSourceType() == null ? "MANUAL" : request.getSourceType());

            totalCalories += calories;
            totalProtein += protein;
            totalFat += fat;
            totalCarbohydrate += carbohydrate;
        }

        jdbcTemplate.update("update diet_record set total_calories=?, total_protein=?, total_fat=?, total_carbohydrate=?, update_time=now() where id=?",
                totalCalories, totalProtein, totalFat, totalCarbohydrate, recordId);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("recordId", recordId);
        result.put("totalCalories", round(totalCalories));
        result.put("totalProtein", round(totalProtein));
        result.put("totalFat", round(totalFat));
        result.put("totalCarbohydrate", round(totalCarbohydrate));
        return result;
    }

    public Map<String, Object> today(Long userId, String date) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "select * from diet_record where user_id = ? and record_date = ? order by id desc",
                userId, date
        );
        return buildDailyResult(records);
    }

    public List<Map<String, Object>> history(Long userId) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "select * from diet_record where user_id = ? order by record_date desc, id desc",
                userId
        );

        Map<String, Map<String, Object>> grouped = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> record : records) {
            String date = String.valueOf(record.get("record_date"));
            Map<String, Object> day = grouped.get(date);
            if (day == null) {
                day = new LinkedHashMap<String, Object>();
                day.put("date", date);
                day.put("summary", buildDailyResult(new ArrayList<Map<String, Object>>()));
                grouped.put(date, day);
            }
            Map<String, Object> summary = (Map<String, Object>) day.get("summary");
            attachRecord(summary, record);
        }
        return new ArrayList<Map<String, Object>>(grouped.values());
    }

    public List<Map<String, Object>> trend(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select date_format(record_date, '%Y-%m-%d') as recordDate, round(sum(total_calories), 2) as totalCalories from diet_record where user_id = ? and record_date >= date_sub(curdate(), interval 6 day) group by record_date order by record_date asc",
                userId
        );

        Map<String, Double> calorieMap = new LinkedHashMap<String, Double>();
        for (Map<String, Object> row : rows) {
            calorieMap.put(String.valueOf(row.get("recordDate")), toDouble(row.get("totalCalories")));
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            String date = sdf.format(calendar.getTime());
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("date", date);
            item.put("label", date.substring(5));
            item.put("totalCalories", round(calorieMap.containsKey(date) ? calorieMap.get(date) : 0D));
            result.add(item);
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return result;
    }

    private Map<String, Object> buildDailyResult(List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("breakfast", new ArrayList<Map<String, Object>>());
        result.put("lunch", new ArrayList<Map<String, Object>>());
        result.put("dinner", new ArrayList<Map<String, Object>>());
        result.put("snack", new ArrayList<Map<String, Object>>());
        result.put("totalCalories", 0D);
        result.put("totalProtein", 0D);
        result.put("totalFat", 0D);
        result.put("totalCarbohydrate", 0D);

        for (Map<String, Object> record : records) {
            attachRecord(result, record);
        }
        return result;
    }

    private void attachRecord(Map<String, Object> result, Map<String, Object> record) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "select * from diet_record_item where record_id = ? order by id desc",
                record.get("id")
        );
        record.put("items", items);
        String mealType = String.valueOf(record.get("meal_type"));
        if ("BREAKFAST".equals(mealType)) {
            ((List<Map<String, Object>>) result.get("breakfast")).add(record);
        } else if ("LUNCH".equals(mealType)) {
            ((List<Map<String, Object>>) result.get("lunch")).add(record);
        } else if ("DINNER".equals(mealType)) {
            ((List<Map<String, Object>>) result.get("dinner")).add(record);
        } else {
            ((List<Map<String, Object>>) result.get("snack")).add(record);
        }
        result.put("totalCalories", round(toDouble(result.get("totalCalories")) + toDouble(record.get("total_calories"))));
        result.put("totalProtein", round(toDouble(result.get("totalProtein")) + toDouble(record.get("total_protein"))));
        result.put("totalFat", round(toDouble(result.get("totalFat")) + toDouble(record.get("total_fat"))));
        result.put("totalCarbohydrate", round(toDouble(result.get("totalCarbohydrate")) + toDouble(record.get("total_carbohydrate"))));
    }

    private double calc(double weight, Object per100g) {
        return round(weight * toDouble(per100g) / 100.0);
    }

    private double toDouble(Object value) {
        return value == null ? 0D : Double.parseDouble(String.valueOf(value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
