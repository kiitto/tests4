package com.prdc.mipower.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A dynamically-detected {@code .dat0} section name plus its records. */
public class DatSection {

    public final String name;
    public final List<DatRecord> records = new ArrayList<>();

    public DatSection(String name) {
        this.name = name;
    }

    /** Union of all field names across every record, order preserved. */
    public List<String> fieldNames() {
        List<String> seen = new ArrayList<>();
        for (DatRecord rec : records) {
            for (String f : rec.fields.keySet()) {
                if (!seen.contains(f)) {
                    seen.add(f);
                }
            }
        }
        return seen;
    }

    public List<DatRecord> recordsWithCondition(Map<String, String> conditionMap) {
        List<DatRecord> result = new ArrayList<>();
        for (DatRecord r : records) {
            if (r.matches(conditionMap)) {
                result.add(r);
            }
        }
        return result;
    }
}
