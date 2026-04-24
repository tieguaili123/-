package com.mint.health.dto;

import java.util.List;

public class DietRecordRequest {
    private Long userId;
    private String recordDate;
    private String mealType;
    private String sourceType;
    private List<FoodAddItem> items;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<FoodAddItem> getItems() {
        return items;
    }

    public void setItems(List<FoodAddItem> items) {
        this.items = items;
    }
}
