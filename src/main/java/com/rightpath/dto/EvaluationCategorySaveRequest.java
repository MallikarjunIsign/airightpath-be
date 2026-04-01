package com.rightpath.dto;

import java.util.List;

import lombok.Data;

@Data
public class EvaluationCategorySaveRequest {

    private String jobPrefix;
    private List<CategoryItem> categories;

    @Data
    public static class CategoryItem {
        private String categoryName;
        private double weight;
        private String description;
    }
}
