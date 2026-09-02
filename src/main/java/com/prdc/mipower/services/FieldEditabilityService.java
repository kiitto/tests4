package com.prdc.mipower.services;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.prdc.mipower.utils.Constants;

/**
 * Which {@code .dat0} fields default to editable vs. read-only in the
 * Workspace table: identifier-looking fields default to read-only, System
 * Specifications/Common Control Options default fully read-only, Slack Bus
 * Angle defaults fully editable, everything else defaults editable.
 * {@link #OVERRIDES} lets specific (section, field) pairs be hand-tuned
 * later without touching the parser.
 *
 * <p>This also underpins the silent WHERE-condition resolution used when
 * writing an edit back to the file: a row's own *locked* (read-only)
 * fields are used as the row's identifying conditions, since edits never
 * mutate them, so they always still match the original file text.
 */
public class FieldEditabilityService {

    private static final Pattern IDENTIFIER_SUFFIX = Pattern.compile("(id|no|number)$");

    /** Per-(section, field) manual overrides -- add entries here to hand-tune specific columns. */
    public static final Map<String, Boolean> OVERRIDES = new HashMap<>();
    // Example: OVERRIDES.put("Bus Data|Bus_ID", false);

    private static String normalizeFieldKey(String fieldName) {
        return fieldName.replaceAll("[_\\s]", "").toLowerCase();
    }

    private boolean defaultEditable(String sectionName, String fieldName) {
        if (Constants.FULLY_EDITABLE_SECTIONS.contains(sectionName)) {
            return true;
        }
        if (Constants.MOSTLY_READONLY_SECTIONS.contains(sectionName)) {
            return false;
        }
        String key = normalizeFieldKey(fieldName);
        if (Constants.EXPLICIT_KEY_FIELDS.contains(key)) {
            return false;
        }
        if (IDENTIFIER_SUFFIX.matcher(key).find()) {
            return false;
        }
        return true;
    }

    public boolean isEditable(String sectionName, String fieldName) {
        Boolean override = OVERRIDES.get(sectionName + "|" + fieldName);
        if (override != null) {
            return override;
        }
        return defaultEditable(sectionName, fieldName);
    }
}
