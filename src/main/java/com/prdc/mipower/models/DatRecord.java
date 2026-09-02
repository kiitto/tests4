package com.prdc.mipower.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prdc.mipower.utils.ValidationUtils;

/**
 * One editable record from a dynamically-parsed {@code .dat0} section -- a
 * data row, or (for {@code subsection}/{@code simple} formats) the whole
 * logical value-set. This is deliberately generic (a {@link #fields} map,
 * not named properties) because the whole point of {@code DatParser} is
 * that it works on ANY MiPower input file's section/field layout without
 * hardcoding what a "Bus" or "Generator" row looks like -- unlike
 * {@link Bus}/{@link Generator}/{@link Line}, which model the fixed-shape
 * *solved results* in an OUT0 file, where hardcoding is appropriate because
 * MiPower's output format doesn't vary the way input files can.
 *
 * <p>{@link #fields} preserves insertion order (LinkedHashMap) so field
 * display order in the GUI table matches the order fields were discovered
 * in the file.
 */
public class DatRecord {

    public final String section;
    public final String formatType; // "simple" | "subsection" | "tabular" | "two_row_table"
    public final LinkedHashMap<String, String> fields;
    public final int blockIndex;
    public final int recordIndex;
    /** Only meaningful for format_type == "two_row_table": field name -> 1 or 2. */
    public final Map<String, Integer> fieldRows;
    /** Ordered list of field names most useful for building WHERE conditions. */
    public final List<String> keyFields;

    public DatRecord(String section, String formatType, LinkedHashMap<String, String> fields,
                      int blockIndex, int recordIndex,
                      Map<String, Integer> fieldRows, List<String> keyFields) {
        this.section = section;
        this.formatType = formatType;
        this.fields = fields;
        this.blockIndex = blockIndex;
        this.recordIndex = recordIndex;
        this.fieldRows = (fieldRows != null) ? fieldRows : new LinkedHashMap<>();
        // Mirrors the original Python's `key_fields or list(fields.keys())[:1]`:
        // an EMPTY list must also fall back to "first field name", not just
        // a null/missing key_fields argument.
        if (keyFields != null && !keyFields.isEmpty()) {
            this.keyFields = keyFields;
        } else {
            List<String> first = new ArrayList<>(fields.keySet());
            this.keyFields = first.isEmpty() ? List.of() : List.of(first.get(0));
        }
    }

    /** Short display label used in the section table / record picker. */
    public String label() {
        if ("simple".equals(formatType)) {
            return section + " (value)";
        }
        if ("subsection".equals(formatType) && fields.size() > 4) {
            return section + " (record)";
        }

        List<String> parts = new ArrayList<>();
        for (String f : keyFields) {
            if (fields.containsKey(f)) {
                parts.add(f + "=" + fields.get(f));
            }
        }
        if (parts.isEmpty()) {
            int i = 0;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (i >= 2) {
                    break;
                }
                parts.add(e.getKey() + "=" + e.getValue());
                i++;
            }
        }
        if (!parts.isEmpty()) {
            return String.join(" ", parts);
        }
        return "Record " + (recordIndex + 1);
    }

    /** conditionMap: {field_name: value} -- all must match this record. */
    public boolean matches(Map<String, String> conditionMap) {
        for (Map.Entry<String, String> e : conditionMap.entrySet()) {
            String field = e.getKey();
            String value = e.getValue();
            if (!fields.containsKey(field) || !ValidationUtils.valuesEqual(fields.get(field), value)) {
                return false;
            }
        }
        return true;
    }
}
