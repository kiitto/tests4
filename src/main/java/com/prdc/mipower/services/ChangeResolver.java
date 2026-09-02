package com.prdc.mipower.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prdc.mipower.models.ChangeRecord;
import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.models.DatRecord;
import com.prdc.mipower.models.DatSection;
import com.prdc.mipower.parser.DatParser;
import com.prdc.mipower.utils.Constants;

/**
 * Answers, for ANY section/record/field in ANY Case Study, one question:
 * "why does this value look the way it does?" -- Unchanged, Inherited
 * (an ancestor changed it and this Case Study never touched it again),
 * Local Change (this Case Study changed it directly, and no ancestor had),
 * or Modified (this Case Study changed it directly, overriding a value an
 * ancestor had already changed).
 *
 * <p>This works for every section and every field -- Bus Data, Transmission
 * Line, Generator Data, Slack Bus Angle, all of them -- because it never
 * hardcodes a section or field name. It walks {@link CaseStudy#modManager}'s
 * active {@link ChangeRecord}s (the same ones {@link CaseStudyManager}
 * replays to build resolved text) and matches them against a record via
 * exactly the same condition-matching logic {@code DatRecord.matches()}
 * already uses to locate rows when applying/undoing an edit -- so "was this
 * row touched" here can never disagree with "was this row touched" when
 * the edit was actually written back to text.
 */
public class ChangeResolver {

    private final CaseStudyManager manager;

    public ChangeResolver(CaseStudyManager manager) {
        this.manager = manager;
    }

    public enum FieldStatus {
        UNCHANGED(Constants.FIELD_UNCHANGED, Constants.COLOR_STATUS_UNCHANGED, "\u26AA"),
        INHERITED(Constants.FIELD_INHERITED, Constants.COLOR_STATUS_INHERITED, "\uD83D\uDD35"),
        LOCAL_CHANGE(Constants.FIELD_LOCAL_CHANGE, Constants.COLOR_STATUS_LOCAL, "\uD83D\uDFE2"),
        MODIFIED(Constants.FIELD_MODIFIED, Constants.COLOR_STATUS_MODIFIED, "\uD83D\uDFE1");

        public final String label;
        public final String color;
        public final String glyph;

        FieldStatus(String label, String color, String glyph) {
            this.label = label;
            this.color = color;
            this.glyph = glyph;
        }

        /** Combines two field statuses into the "stronger" one, for record-level rollup. */
        static FieldStatus stronger(FieldStatus a, FieldStatus b) {
            return (a.ordinal() >= b.ordinal()) ? a : b;
        }
    }

    // --------------------------------------------------------------------- //
    // Field level
    // --------------------------------------------------------------------- //

    public FieldStatus fieldStatus(CaseStudy cs, String section, DatRecord record, String fieldName) {
        boolean inherited = matchesAnyAncestor(cs, section, record, fieldName);
        boolean local = matchesOwnHistory(cs, section, record, fieldName);
        if (inherited && local) {
            return FieldStatus.MODIFIED;
        }
        if (local) {
            return FieldStatus.LOCAL_CHANGE;
        }
        if (inherited) {
            return FieldStatus.INHERITED;
        }
        return FieldStatus.UNCHANGED;
    }

    private boolean matchesAnyAncestor(CaseStudy cs, String section, DatRecord record, String fieldName) {
        for (CaseStudy ancestor : manager.ancestorChain(cs)) {
            if (changeListTouchesField(ancestor.modManager.getPending(), section, record, fieldName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOwnHistory(CaseStudy cs, String section, DatRecord record, String fieldName) {
        return changeListTouchesField(cs.modManager.getPending(), section, record, fieldName);
    }

    private boolean changeListTouchesField(List<ChangeRecord> changes, String section, DatRecord record,
                                            String fieldName) {
        for (ChangeRecord c : changes) {
            if (!c.section.equals(section)) {
                continue;
            }
            String changedField = (c.field != null) ? c.field : "Value";
            if (!changedField.equals(fieldName)) {
                continue;
            }
            if (record.matches(c.conditions)) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------- //
    // Record level
    // --------------------------------------------------------------------- //

    public FieldStatus recordStatus(CaseStudy cs, String section, DatRecord record) {
        FieldStatus overall = FieldStatus.UNCHANGED;
        for (String field : record.fields.keySet()) {
            FieldStatus fs = fieldStatus(cs, section, record, field);
            overall = FieldStatus.stronger(overall, fs);
        }
        return overall;
    }

    // --------------------------------------------------------------------- //
    // Section level (the "Section Record Explorer" panel)
    // --------------------------------------------------------------------- //

    public record SectionSummary(String sectionName, int totalRecords, int editableRecords,
                                  int modifiedRecords, int inheritedRecords, int localChangeRecords,
                                  int unchangedRecords) {
    }

    public SectionSummary sectionSummary(CaseStudy cs, String sectionName, FieldEditabilityService editability) {
        DatSection section = cs.parser.getSection(sectionName);
        if (section == null) {
            return new SectionSummary(sectionName, 0, 0, 0, 0, 0, 0);
        }
        int editableRecords = 0;
        int modified = 0;
        int inherited = 0;
        int local = 0;
        int unchanged = 0;
        for (DatRecord record : section.records) {
            boolean anyEditable = false;
            for (String field : record.fields.keySet()) {
                if (editability.isEditable(sectionName, field)) {
                    anyEditable = true;
                    break;
                }
            }
            if (anyEditable) {
                editableRecords++;
            }
            switch (recordStatus(cs, sectionName, record)) {
                case MODIFIED -> modified++;
                case INHERITED -> inherited++;
                case LOCAL_CHANGE -> local++;
                case UNCHANGED -> unchanged++;
            }
        }
        return new SectionSummary(sectionName, section.records.size(), editableRecords,
                modified, inherited, local, unchanged);
    }

    // --------------------------------------------------------------------- //
    // Record detail (the "Record Details" panel: Original vs Current per field)
    // --------------------------------------------------------------------- //

    public record FieldDetail(String fieldName, boolean editable, String originalValue, String currentValue,
                               FieldStatus status) {
        public boolean changed() {
            return status != FieldStatus.UNCHANGED;
        }
    }

    /**
     * Full field-by-field detail for one record: original value = this
     * Case Study's REFERENCE value for the same row (matched by this
     * record's own identifying/locked fields, which never change across
     * the hierarchy) -- that is, the immediate parent's current resolved
     * data for a child Case Study (e.g. Rahul's live values for Sham), or
     * the Base File's value for a root Case Study. Never unconditionally
     * the Base File -- otherwise a child's "Original" column would ignore
     * everything its parent already changed. current value = this Case
     * Study's value right now.
     */
    public List<FieldDetail> recordDetail(CaseStudy cs, String sectionName, DatRecord record,
                                           FieldEditabilityService editability) {
        List<FieldDetail> details = new ArrayList<>();
        DatRecord baseRecord = findInReference(cs, sectionName, record, editability);
        for (Map.Entry<String, String> e : record.fields.entrySet()) {
            String fieldName = e.getKey();
            String current = e.getValue();
            String original = (baseRecord != null) ? baseRecord.fields.getOrDefault(fieldName, current) : current;
            FieldStatus status = fieldStatus(cs, sectionName, record, fieldName);
            details.add(new FieldDetail(fieldName, editability.isEditable(sectionName, fieldName),
                    original, current, status));
        }
        return details;
    }

    /**
     * Locates {@code record}'s counterpart in {@code cs}'s reference --
     * the parent Case Study's current resolved text if {@code cs} has one,
     * otherwise the Base File -- so "Original" always reflects what the
     * user actually chose as the reference when creating {@code cs}, kept
     * live as that reference is itself edited later.
     */
    private DatRecord findInReference(CaseStudy cs, String sectionName, DatRecord record,
                                       FieldEditabilityService editability) {
        DatSection refSection = referenceParser(cs).getSection(sectionName);
        if (refSection == null) {
            return null;
        }
        Map<String, String> lockedConditions = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : record.fields.entrySet()) {
            if (!editability.isEditable(sectionName, e.getKey())) {
                lockedConditions.put(e.getKey(), e.getValue());
            }
        }
        if (lockedConditions.isEmpty()) {
            for (String kf : record.keyFields) {
                if (record.fields.containsKey(kf)) {
                    lockedConditions.put(kf, record.fields.get(kf));
                }
            }
        }
        List<DatRecord> matches = refSection.recordsWithCondition(lockedConditions);
        return (matches.size() == 1) ? matches.get(0) : null;
    }

    /** Parses {@code cs}'s reference text (parent's live resolved data, or the Base File for a root). */
    private DatParser referenceParser(CaseStudy cs) {
        DatParser parser = new DatParser();
        parser.loadText(manager.resolveReferenceText(cs));
        parser.parse();
        return parser;
    }
}
