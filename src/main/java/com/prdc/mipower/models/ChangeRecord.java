package com.prdc.mipower.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single pending (or applied) modification, built entirely by the GUI --
 * the user never types raw mod-file syntax themselves. One immutable fact
 * about one edit; {@link HistoryEntry} wraps this with a mutable status
 * (Pending/Saved/Undone/Deleted) on top, entirely separately.
 *
 * <p>formatType: "simple" | "subsection" | "tabular" | "two_row_table".
 * conditions: {field_name: value} used to locate the row(s) to change
 * (only meaningful for "tabular"/"two_row_table"). targetRow: 1 or 2, only
 * meaningful for "two_row_table".
 */
public class ChangeRecord {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public final String section;
    public final String field; // may be null for "simple" format
    public final String oldValue;
    public final String newValue;
    public final String formatType;
    public final LinkedHashMap<String, String> conditions;
    public final Integer targetRow;
    public final String recordLabel;
    public final String timestamp;

    public ChangeRecord(String section, String field, String oldValue, String newValue, String formatType,
                         Map<String, String> conditions, Integer targetRow, String recordLabel) {
        this.section = section;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.formatType = formatType;
        this.conditions = (conditions != null) ? new LinkedHashMap<>(conditions) : new LinkedHashMap<>();
        this.targetRow = targetRow;
        this.recordLabel = (recordLabel != null) ? recordLabel : "";
        this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    /** {Field=Value, Field=Value} display string for conditions, or "(none)". */
    public String formatConditionStr() {
        if (conditions.isEmpty()) {
            return "(none)";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : conditions.entrySet()) {
            parts.add(e.getKey() + "=" + e.getValue());
        }
        return String.join(", ", parts);
    }

    @Override
    public String toString() {
        return "ChangeRecord(section=" + section + ", field=" + field
                + ", " + oldValue + " -> " + newValue + ")";
    }
}
