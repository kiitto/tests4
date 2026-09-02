package com.prdc.mipower.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.prdc.mipower.utils.ValidationUtils;

/**
 * Applies modifications -- built entirely by the GUI, never hand-typed
 * mod-file syntax -- to a MiPower {@code .dat0} file's raw text. Port of
 * the Python project's {@code modifier.py} module-level {@code modifyInput()}
 * and its four format-specific handlers.
 *
 * <p>Package-private implementation detail of {@link ModificationManager}
 * (which is what the rest of the app actually talks to) -- kept as its own
 * class instead of folded into ModificationManager for single
 * responsibility: this class only knows how to rewrite text given a list of
 * modifications; it holds no state of its own and doesn't know about
 * pending lists, undo/redo, or Case Studies at all.
 *
 * <p>Deliberately does its own whole-file section search (not bounded by
 * DatParser's per-section slicing), exactly like the Python original,
 * since this writes back into the real file text.
 */
final class ModifierEngine {

    private ModifierEngine() {
    }

    /** One modification to apply, shaped like ChangeRecord's own data. */
    record Modification(String section, String field, String newValue, String formatType,
                         List<String> conditions, Integer targetRow) {
    }

    record ModifyResult(String text, int appliedCount) {
    }

    // --------------------------------------------------------------------- //
    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String lstripPercent(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '%') {
            i++;
        }
        return s.substring(i);
    }

    /** Locates the line index of a section title such as "%Bus Data". */
    static int findSection(List<String> lines, String sectionName) {
        String target = normalize(sectionName);

        for (int i = 0; i < lines.size(); i++) {
            String stripped = lines.get(i).strip();
            if (stripped.startsWith("%")) {
                String content = lstripPercent(stripped).strip();
                if (normalize(content).equals(target)) {
                    return i;
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String stripped = lines.get(i).strip();
            if (stripped.startsWith("%") && normalize(stripped).contains(target)) {
                return i;
            }
        }
        return -1;
    }

    private record NumericRun(List<Double> values, int endIdx) {
    }

    private static NumericRun extractAllNumericValues(List<String> lines, int startIdx) {
        List<Double> allValues = new ArrayList<>();
        int endIdx = startIdx;

        for (int i = startIdx; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("%")) {
                endIdx = i - 1;
                break;
            }
            List<Double> values = ValidationUtils.extractNumericValues(line);
            if (!values.isEmpty()) {
                allValues.addAll(values);
                endIdx = i;
            }
        }
        return new NumericRun(allValues, endIdx);
    }

    /** Splits on `(\s+)` keeping the whitespace runs as their own elements, like Python's re.split. */
    private static final Pattern WS_SPLIT = Pattern.compile("(\\s+)");

    private static List<String> splitKeepDelimiters(String line) {
        List<String> parts = new ArrayList<>();
        Matcher m = WS_SPLIT.matcher(line);
        int last = 0;
        while (m.find()) {
            parts.add(line.substring(last, m.start()));
            parts.add(m.group());
            last = m.end();
        }
        parts.add(line.substring(last));
        return parts;
    }

    /** Replaces the Nth NUMERIC value (0-based) in a line, preserving spacing. */
    private static String replaceValueInLine(String line, int idx, String newValue) {
        List<String> parts = splitKeepDelimiters(line);
        List<Integer> numericPositions = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (ValidationUtils.tryParsePyFloat(parts.get(i)) != null) {
                numericPositions.add(i);
            }
        }
        if (idx < numericPositions.size()) {
            parts.set(numericPositions.get(idx), newValue);
        }
        return String.join("", parts);
    }

    private record TokenSplit(List<String> parts, List<Integer> tokenPositions) {
    }

    private static TokenSplit splitLineTokens(String line) {
        List<String> parts = splitKeepDelimiters(line);
        List<Integer> tokenPositions = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            if (!p.isEmpty() && !p.isBlank()) {
                tokenPositions.add(i);
            }
        }
        return new TokenSplit(parts, tokenPositions);
    }

    private record TokenReplaceResult(String line, boolean ok) {
    }

    /** Replaces the Nth whitespace-separated token (0-based) in a line. */
    private static TokenReplaceResult replaceTokenInLine(String line, int tokenIdx, String newValue) {
        TokenSplit split = splitLineTokens(line);
        if (tokenIdx >= 0 && tokenIdx < split.tokenPositions().size()) {
            List<String> parts = new ArrayList<>(split.parts());
            parts.set(split.tokenPositions().get(tokenIdx), newValue);
            return new TokenReplaceResult(String.join("", parts), true);
        }
        return new TokenReplaceResult(line, false);
    }

    private record HeaderBlock(List<Integer> headerIndices, int dataStart) {
    }

    private static HeaderBlock collectHeaderBlock(List<String> lines, int startIdx) {
        List<Integer> headerIndices = new ArrayList<>();
        int i = startIdx;
        while (i < lines.size()) {
            String stripped = lines.get(i).strip();
            if (stripped.startsWith("%")) {
                headerIndices.add(i);
                i++;
            } else if (stripped.isEmpty()) {
                i++;
            } else {
                return new HeaderBlock(headerIndices, i);
            }
        }
        return new HeaderBlock(headerIndices, -1);
    }

    private static List<String> buildFlatHeaderFields(List<String> lines, List<Integer> headerIndices) {
        List<String> headerText = new ArrayList<>();
        for (int idx : headerIndices) {
            headerText.add(lines.get(idx));
        }
        LinkedHashMap<Integer, String> numberedMap = ValidationUtils.buildNumberedFieldMap(headerText);
        if (!numberedMap.isEmpty()) {
            List<Integer> nums = new ArrayList<>(numberedMap.keySet());
            nums.sort(Integer::compareTo);
            List<String> fields = new ArrayList<>();
            for (int n : nums) {
                fields.add(numberedMap.get(n));
            }
            return fields;
        }

        List<String> fields = new ArrayList<>();
        for (int idx : headerIndices) {
            String content = lstripPercent(lines.get(idx).strip()).strip();
            fields.addAll(ValidationUtils.tokenizeHeader(content));
        }
        return fields;
    }

    private static Integer lookupNumberedField(Map<Integer, String> fieldMap, String fieldName) {
        String target = fieldName.strip().toLowerCase();
        for (Map.Entry<Integer, String> e : fieldMap.entrySet()) {
            if (e.getValue().strip().toLowerCase().equals(target)) {
                return e.getKey();
            }
        }
        for (Map.Entry<Integer, String> e : fieldMap.entrySet()) {
            if (e.getValue().strip().toLowerCase().contains(target)) {
                return e.getKey();
            }
        }
        return null;
    }

    // --------------------------------------------------------------------- //
    // Format-specific modification handlers
    // --------------------------------------------------------------------- //

    private static int applySimpleMod(List<String> lines, List<String> modifiedLines,
                                       int searchStart, String section, String newValue) {
        for (int i = searchStart; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("%")) {
                continue;
            }
            if (!ValidationUtils.extractNumericValues(line).isEmpty()) {
                modifiedLines.set(i, replaceValueInLine(modifiedLines.get(i), 0, newValue));
                return 1;
            }
        }
        System.out.println("Warning: No data found to modify in section '" + section + "'");
        return 0;
    }

    private static int applySubsectionMod(List<String> lines, List<String> modifiedLines,
                                           int searchStart, String section, String fieldName, String newValue) {
        int i = searchStart;

        while (i < lines.size()) {
            HeaderBlock hb = collectHeaderBlock(lines, i);
            if (hb.dataStart() == -1) {
                break;
            }

            List<String> headerText = new ArrayList<>();
            for (int h : hb.headerIndices()) {
                headerText.add(lines.get(h));
            }
            LinkedHashMap<Integer, String> fieldMap = ValidationUtils.buildNumberedFieldMap(headerText);

            if (fieldMap.isEmpty()) {
                break;
            }

            Integer targetNum = lookupNumberedField(fieldMap, fieldName);

            if (targetNum != null) {
                NumericRun run = extractAllNumericValues(lines, hb.dataStart());
                List<Integer> sortedNums = new ArrayList<>(fieldMap.keySet());
                sortedNums.sort(Integer::compareTo);
                List<String> fieldList = new ArrayList<>();
                for (int n : sortedNums) {
                    fieldList.add(fieldMap.get(n));
                }
                int targetIdx = fieldList.indexOf(fieldMap.get(targetNum));
                if (targetIdx < 0) {
                    targetIdx = targetNum - 1;
                }

                if (targetIdx >= 0 && targetIdx < run.values().size()) {
                    int valueCount = 0;
                    for (int j = hb.dataStart(); j <= run.endIdx(); j++) {
                        String line = lines.get(j).strip();
                        if (line.isEmpty() || line.startsWith("%")) {
                            continue;
                        }
                        List<Double> lineValues = ValidationUtils.extractNumericValues(line);
                        if (valueCount + lineValues.size() > targetIdx) {
                            int localIdx = targetIdx - valueCount;
                            modifiedLines.set(j, replaceValueInLine(modifiedLines.get(j), localIdx, newValue));
                            return 1;
                        }
                        valueCount += lineValues.size();
                    }
                }

                System.out.println("Warning: Field '" + fieldName
                        + "' resolved to an out-of-range value in section '" + section + "'");
                return 0;
            }

            NumericRun skipRun = extractAllNumericValues(lines, hb.dataStart());
            i = skipRun.endIdx() + 1;
        }

        System.out.println("Warning: Field '" + fieldName + "' not found in section '" + section + "'");
        return 0;
    }

    private static Map<Integer, String> parseConditionMap(List<String> conditions, List<String> headerFields) {
        Map<Integer, String> parsed = new LinkedHashMap<>();
        for (String condition : conditions) {
            if (!condition.contains("=")) {
                continue;
            }
            int eq = condition.indexOf('=');
            String condField = condition.substring(0, eq).strip();
            String condValue = condition.substring(eq + 1).strip();
            int condIdx = ValidationUtils.findFieldIndex(condField, headerFields);
            if (condIdx >= 0) {
                parsed.put(condIdx, condValue);
            } else {
                System.out.println("Warning: Condition field '" + condField + "' not found in header");
            }
        }
        return parsed;
    }

    private static boolean rowMatches(List<String> tokens, Map<Integer, String> conditionMap) {
        for (Map.Entry<Integer, String> e : conditionMap.entrySet()) {
            int condIdx = e.getKey();
            String condValue = e.getValue();
            if (condIdx >= tokens.size() || !ValidationUtils.valuesEqual(tokens.get(condIdx), condValue)) {
                return false;
            }
        }
        return true;
    }

    private static int applyTabularMod(List<String> lines, List<String> modifiedLines, int searchStart,
                                        String section, String fieldName, String newValue, List<String> conditions) {
        HeaderBlock hb = collectHeaderBlock(lines, searchStart);
        if (hb.headerIndices().isEmpty() || hb.dataStart() == -1) {
            System.out.println("Warning: No header/data found in section '" + section + "'");
            return 0;
        }

        List<String> headerFields = buildFlatHeaderFields(lines, hb.headerIndices());
        int columnIdx = ValidationUtils.findFieldIndex(fieldName, headerFields);
        if (columnIdx == -1) {
            System.out.println("Warning: Column '" + fieldName + "' not found in section '" + section + "'");
            return 0;
        }

        Map<Integer, String> conditionMap = parseConditionMap(conditions, headerFields);

        int applied = 0;
        for (int i = hb.dataStart(); i < lines.size(); i++) {
            String line = modifiedLines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("%")) {
                break;
            }
            List<String> tokens = ValidationUtils.rowTokens(line);
            List<String> origTokens = ValidationUtils.rowTokens(lines.get(i).strip());
            if (rowMatches(tokens, conditionMap) || rowMatches(origTokens, conditionMap)) {
                TokenReplaceResult r = replaceTokenInLine(modifiedLines.get(i), columnIdx, newValue);
                modifiedLines.set(i, r.line());
                if (r.ok()) {
                    applied++;
                }
            }
        }

        if (applied == 0) {
            System.out.println("Warning: No row matched the given condition(s) in section '" + section + "'");
        }
        return applied;
    }

    private static int applyTwoRowTableMod(List<String> lines, List<String> modifiedLines, int searchStart,
                                            String section, String fieldName, String newValue,
                                            List<String> conditions, Integer targetRow) {
        HeaderBlock hb = collectHeaderBlock(lines, searchStart);
        if (hb.headerIndices().size() < 2 || hb.dataStart() == -1) {
            System.out.println("Warning: Two header lines not found in section '" + section + "'");
            return 0;
        }

        int row1HeaderIdx = hb.headerIndices().get(hb.headerIndices().size() - 2);
        int row2HeaderIdx = hb.headerIndices().get(hb.headerIndices().size() - 1);
        List<String> row1Fields = ValidationUtils.tokenizeHeader(
                lstripPercent(lines.get(row1HeaderIdx).strip()).strip());
        List<String> row2Fields = ValidationUtils.tokenizeHeader(
                lstripPercent(lines.get(row2HeaderIdx).strip()).strip());

        int columnIdx;
        Integer resolvedRow;
        if (targetRow != null && targetRow == 1) {
            columnIdx = ValidationUtils.findFieldIndex(fieldName, row1Fields);
            resolvedRow = (columnIdx >= 0) ? 1 : null;
        } else if (targetRow != null && targetRow == 2) {
            columnIdx = ValidationUtils.findFieldIndex(fieldName, row2Fields);
            resolvedRow = (columnIdx >= 0) ? 2 : null;
        } else {
            columnIdx = ValidationUtils.findFieldIndex(fieldName, row1Fields);
            if (columnIdx >= 0) {
                resolvedRow = 1;
            } else {
                columnIdx = ValidationUtils.findFieldIndex(fieldName, row2Fields);
                resolvedRow = (columnIdx >= 0) ? 2 : null;
            }
        }

        if (resolvedRow == null) {
            System.out.println("Warning: Column '" + fieldName + "' not found in section '" + section + "'");
            return 0;
        }

        Map<Integer, String> conditionMap = parseConditionMap(conditions, row1Fields);

        int applied = 0;
        int i = hb.dataStart();
        while (i < lines.size()) {
            String line1 = lines.get(i).strip();
            if (line1.startsWith("%")) {
                break;
            }
            if (line1.isEmpty()) {
                i++;
                continue;
            }

            List<String> row1Tokens = ValidationUtils.rowTokens(line1);

            int line2Idx = -1;
            int j = i + 1;
            while (j < lines.size()) {
                String candidate = lines.get(j).strip();
                if (candidate.isEmpty()) {
                    j++;
                    continue;
                }
                if (candidate.startsWith("%")) {
                    break;
                }
                line2Idx = j;
                break;
            }

            if (rowMatches(row1Tokens, conditionMap)) {
                boolean ok;
                if (resolvedRow == 1) {
                    TokenReplaceResult r = replaceTokenInLine(modifiedLines.get(i), columnIdx, newValue);
                    modifiedLines.set(i, r.line());
                    ok = r.ok();
                } else if (line2Idx >= 0) {
                    TokenReplaceResult r = replaceTokenInLine(modifiedLines.get(line2Idx), columnIdx, newValue);
                    modifiedLines.set(line2Idx, r.line());
                    ok = r.ok();
                } else {
                    ok = false;
                    System.out.println("Warning: Row 2 missing for a matching record in section '" + section + "'");
                }
                if (ok) {
                    applied++;
                }
            }

            i = (line2Idx >= 0) ? line2Idx + 1 : i + 1;
        }

        if (applied == 0) {
            System.out.println("Warning: No row matched the given condition(s) in section '" + section + "'");
        }
        return applied;
    }

    // --------------------------------------------------------------------- //
    static ModifyResult modifyInput(String data, List<Modification> modifications) {
        List<String> lines = new ArrayList<>(List.of(data.split("\r\n|\r|\n", -1)));
        List<String> modifiedLines = new ArrayList<>(lines);
        int appliedCount = 0;

        for (Modification mod : modifications) {
            String section = mod.section();
            String fieldName = mod.field();
            String newValue = mod.newValue();
            String formatType = mod.formatType();

            int sectionLine = findSection(lines, section);
            if (sectionLine == -1) {
                System.out.println("Warning: Section '" + section + "' not found");
                continue;
            }

            int searchStart = sectionLine + 1;

            switch (formatType) {
                case "simple" -> appliedCount += applySimpleMod(lines, modifiedLines, searchStart, section, newValue);
                case "subsection" -> appliedCount += applySubsectionMod(lines, modifiedLines, searchStart, section,
                        fieldName, newValue);
                case "tabular" -> {
                    List<String> conditions = (mod.conditions() != null) ? mod.conditions() : List.of();
                    appliedCount += applyTabularMod(lines, modifiedLines, searchStart, section,
                            fieldName, newValue, conditions);
                }
                case "two_row_table" -> {
                    List<String> conditions = (mod.conditions() != null) ? mod.conditions() : List.of();
                    appliedCount += applyTwoRowTableMod(lines, modifiedLines, searchStart, section,
                            fieldName, newValue, conditions, mod.targetRow());
                }
                default -> {
                    // Unknown format type: nothing to apply.
                }
            }
        }

        return new ModifyResult(String.join("\n", modifiedLines), appliedCount);
    }
}
