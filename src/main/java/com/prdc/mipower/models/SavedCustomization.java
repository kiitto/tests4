package com.prdc.mipower.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a saved user customization record containing chosen case studies,
 * configured analytics charts, filters, insights, and document notes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SavedCustomization {

    public String id;
    public String name;
    public String description;
    public String createdAt;
    public List<String> selectedCaseIds = new ArrayList<>();
    public List<SavedChartItem> chartItems = new ArrayList<>();

    public SavedCustomization() {
        this.createdAt = LocalDateTime.now().toString();
    }

    public SavedCustomization(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = LocalDateTime.now().toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SavedChartItem {
        public String categoryId;
        public String categoryTitle;
        public String chartType;
        public String xAxisLabel;
        public String yAxisLabel;
        public String unit;
        public String insightText;
        public String userNotes;
        public List<String> activeCaseNames = new ArrayList<>();

        public SavedChartItem() {
        }

        public SavedChartItem(String categoryId, String categoryTitle, String chartType,
                              String xAxisLabel, String yAxisLabel, String unit,
                              String insightText, String userNotes, List<String> activeCaseNames) {
            this.categoryId = categoryId;
            this.categoryTitle = categoryTitle;
            this.chartType = chartType;
            this.xAxisLabel = xAxisLabel;
            this.yAxisLabel = yAxisLabel;
            this.unit = unit;
            this.insightText = insightText;
            this.userNotes = userNotes;
            if (activeCaseNames != null) {
                this.activeCaseNames.addAll(activeCaseNames);
            }
        }
    }
}
