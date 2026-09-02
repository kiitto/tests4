package com.prdc.mipower.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.prdc.mipower.models.DatRecord;
import com.prdc.mipower.models.DatSection;
import com.prdc.mipower.utils.Constants;
import com.prdc.mipower.utils.FileUtils;

import static com.prdc.mipower.utils.ValidationUtils.*;

/**
 * Reads a MiPower {@code .dat0} input file and builds a fully dynamic,
 * navigable model of its sections, records, and fields -- so the GUI never
 * needs to know column numbers or field positions in advance. Nothing about
 * column layout is hardcoded; the only assumption is a *default* ordered
 * list of section titles ({@link Constants#DEFAULT_SECTION_ORDER}) used
 * purely to decide display order for sections that exist in the file.
 * Sections not in that list are still detected and appended automatically.
 *
 * <p><b>Detection strategy</b> (fully automatic, no hardcoded fields):
 * <ol>
 *   <li>Locate every "section title" comment line in the file.</li>
 *   <li>For each section, walk its bounded region block by block:
 *     <ul>
 *       <li>No header, bare data line -&gt; {@code simple} (e.g. Slack Bus Angle).</li>
 *       <li>Numbered "N.FieldName" header whose data spans several physical
 *           lines -&gt; {@code subsection} (e.g. System Specifications).</li>
 *       <li>Two consecutive header lines, row 2 a shorter numeric
 *           continuation of row 1 -&gt; {@code two_row_table} (e.g.
 *           Generator Frequency Characteristics).</li>
 *       <li>Anything else -&gt; {@code tabular}: one header, one or more
 *           independent data rows (e.g. Bus Data, Transmission Line,
 *           Generator Data).</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public class DatParser {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\d+\\s*[.:]?");

    private String filePath;
    private String rawText = "";
    private List<String> lines = new ArrayList<>();
    private final LinkedHashMap<String, DatSection> sections = new LinkedHashMap<>();

    public String getRawText() {
        return rawText;
    }

    public String getFilePath() {
        return filePath;
    }

    // ------------------------------------------------------------------- //
    public DatParser load(String path) throws IOException {
        this.filePath = path;
        this.rawText = FileUtils.readText(path);
        this.lines = new ArrayList<>(Arrays.asList(splitLines(rawText)));
        return this;
    }

    public DatParser loadText(String text) {
        this.filePath = null;
        this.rawText = text;
        this.lines = new ArrayList<>(Arrays.asList(splitLines(text)));
        return this;
    }

    /**
     * Splits text into lines the way Python's universal-newline text-mode
     * {@code open()} + {@code str.split('\n')} effectively does -- tolerant
     * of "\r\n" and bare "\r" as well as "\n" -- rather than a naive
     * {@code split("\n")}, which would leave a stray trailing "\r" on every
     * line if the source file uses Windows line endings. {@code
     * Files.readString} does not perform Python's automatic newline
     * translation, so this has to be done explicitly here.
     */
    private static String[] splitLines(String text) {
        if (text.isEmpty()) {
            return new String[]{""};
        }
        return text.split("\r\n|\r|\n", -1);
    }

    // ------------------------------------------------------------------- //
    private LinkedHashMap<String, Integer> findSectionTitles() {
        LinkedHashMap<String, Integer> found = new LinkedHashMap<>();

        List<Integer> matchPositions = new ArrayList<>();
        List<String> matchNames = new ArrayList<>();
        for (String name : Constants.DEFAULT_SECTION_ORDER) {
            String target = normalize(name);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentLine(line) && normalize(commentText(line)).equals(target)) {
                    matchPositions.add(i);
                    matchNames.add(name);
                    break;
                }
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < matchPositions.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Integer.compare(matchPositions.get(a), matchPositions.get(b)));
        for (int idx : order) {
            found.put(matchNames.get(idx), matchPositions.get(idx));
        }

        Set<Integer> claimedLines = new HashSet<>(found.values());

        List<Map.Entry<String, Integer>> orderedNames = new ArrayList<>(found.entrySet());
        orderedNames.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));

        List<int[]> scanRanges = new ArrayList<>();
        int cursor = 0;
        for (Map.Entry<String, Integer> e : orderedNames) {
            scanRanges.add(new int[]{cursor, e.getValue()});
            cursor = e.getValue() + 1;
        }
        scanRanges.add(new int[]{cursor, lines.size()});

        for (int[] range : scanRanges) {
            int start = range[0];
            int end = range[1];
            for (int i = start; i < Math.min(end, lines.size()); i++) {
                if (claimedLines.contains(i)) {
                    continue;
                }
                String line = lines.get(i);
                if (!isCommentLine(line)) {
                    continue;
                }
                String text = commentText(line);
                if (text.isEmpty() || text.contains("=") || text.contains("(") || text.contains(")")
                        || text.contains("<") || text.contains(">")) {
                    continue;
                }
                if (LEADING_NUMBER.matcher(text).lookingAt()) {
                    continue;
                }
                if (rowTokens(text).size() > 6) {
                    continue;
                }
                int k = i - 1;
                boolean precededByComment = false;
                while (k >= start) {
                    if (lines.get(k).strip().isEmpty()) {
                        k--;
                        continue;
                    }
                    precededByComment = isCommentLine(lines.get(k));
                    break;
                }
                if (precededByComment) {
                    continue;
                }
                int j = i + 1;
                boolean hasDataAfter = false;
                while (j < lines.size() && j < end) {
                    if (lines.get(j).strip().isEmpty() || isCommentLine(lines.get(j))) {
                        j++;
                        continue;
                    }
                    hasDataAfter = true;
                    break;
                }
                if (hasDataAfter && !found.containsKey(text)) {
                    found.put(text, i);
                    claimedLines.add(i);
                }
            }
        }

        List<Map.Entry<String, Integer>> finalOrder = new ArrayList<>(found.entrySet());
        finalOrder.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : finalOrder) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    // ------------------------------------------------------------------- //
    public Map<String, DatSection> parse() {
        sections.clear();
        LinkedHashMap<String, Integer> titleLines = findSectionTitles();
        List<String> namesInOrder = new ArrayList<>(titleLines.keySet());
        List<Integer> boundaries = new ArrayList<>(titleLines.values());
        boundaries.add(lines.size());

        for (int idx = 0; idx < namesInOrder.size(); idx++) {
            String name = namesInOrder.get(idx);
            int start = titleLines.get(name) + 1;
            int end = boundaries.get(idx + 1);
            DatSection section = new DatSection(name);
            parseSectionRegion(section, start, end);
            sections.put(name, section);
        }
        return sections;
    }

    // ------------------------------------------------------------------- //
    private record HeaderBlock(List<Integer> headerIndices, int dataStart) {
    }

    private HeaderBlock collectHeaderBlock(int start, int end) {
        List<Integer> headerIndices = new ArrayList<>();
        int i = start;
        while (i < end) {
            String stripped = lines.get(i).strip();
            if (isCommentLine(lines.get(i))) {
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

    private List<Integer> collectDataLines(int start, int end) {
        List<Integer> idxs = new ArrayList<>();
        int i = start;
        while (i < end) {
            String stripped = lines.get(i).strip();
            if (stripped.isEmpty()) {
                i++;
                continue;
            }
            if (isCommentLine(lines.get(i))) {
                break;
            }
            idxs.add(i);
            i++;
        }
        return idxs;
    }

    // ------------------------------------------------------------------- //
    private void parseSectionRegion(DatSection section, int start, int end) {
        int pos = start;
        int blockIndex = 0;

        while (pos < end) {
            while (pos < end && lines.get(pos).strip().isEmpty()) {
                pos++;
            }
            if (pos >= end) {
                break;
            }

            HeaderBlock hb = collectHeaderBlock(pos, end);
            List<Integer> headerIndices = hb.headerIndices();
            int dataStart = hb.dataStart();

            if (dataStart == -1) {
                break;
            }

            if (headerIndices.isEmpty()) {
                List<Integer> dataIdxs = collectDataLines(dataStart, end);
                if (dataIdxs.isEmpty()) {
                    break;
                }
                String valueLine = lines.get(dataIdxs.get(0)).strip();
                List<String> valueTokens = rowTokens(valueLine);
                LinkedHashMap<String, String> fields = new LinkedHashMap<>();
                fields.put("Value", valueTokens.isEmpty() ? valueLine : valueTokens.get(0));
                DatRecord rec = new DatRecord(section.name, "simple", fields,
                        blockIndex, 0, null, List.of());
                section.records.add(rec);
                pos = dataIdxs.get(dataIdxs.size() - 1) + 1;
                blockIndex++;
                continue;
            }

            List<String> headerText = new ArrayList<>();
            for (int h : headerIndices) {
                headerText.add(lines.get(h));
            }
            LinkedHashMap<Integer, String> numberedMap = buildNumberedFieldMap(headerText);

            if (headerIndices.size() >= 2) {
                TwoRowInfo twoRow = tryTwoRow(headerIndices, dataStart, end);
                if (twoRow != null) {
                    LinkedHashMap<String, Integer> fieldRows = new LinkedHashMap<>();
                    for (String f : twoRow.row1Fields()) {
                        fieldRows.put(f, 1);
                    }
                    for (String f : twoRow.row2Fields()) {
                        fieldRows.put(f, 2);
                    }
                    int rIdx = 0;
                    for (RowPair pair : twoRow.pairs()) {
                        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
                        List<String> toks1 = rowTokens(lines.get(pair.l1()));
                        for (int i = 0; i < twoRow.row1Fields().size(); i++) {
                            if (i < toks1.size()) {
                                fields.put(twoRow.row1Fields().get(i), toks1.get(i));
                            }
                        }
                        if (pair.l2() != null) {
                            List<String> toks2 = rowTokens(lines.get(pair.l2()));
                            for (int i = 0; i < twoRow.row2Fields().size(); i++) {
                                if (i < toks2.size()) {
                                    fields.put(twoRow.row2Fields().get(i), toks2.get(i));
                                }
                            }
                        }
                        List<String> key = twoRow.row1Fields().size() >= 2
                                ? new ArrayList<>(twoRow.row1Fields().subList(0, 2))
                                : new ArrayList<>(twoRow.row1Fields());
                        DatRecord rec = new DatRecord(section.name, "two_row_table", fields,
                                blockIndex, rIdx, fieldRows, key);
                        section.records.add(rec);
                        rIdx++;
                    }
                    pos = twoRow.lastIdx() + 1;
                    blockIndex++;
                    continue;
                }
            }

            List<String> fieldList = new ArrayList<>();
            if (!numberedMap.isEmpty()) {
                List<Integer> nums = new ArrayList<>(numberedMap.keySet());
                nums.sort(Integer::compareTo);
                for (int n : nums) {
                    fieldList.add(numberedMap.get(n));
                }
            } else {
                // Filter headerIndices to find true column header lines (ignoring pure divider lines or long descriptive text)
                List<Integer> validHeaderLines = new ArrayList<>();
                for (int h : headerIndices) {
                    String ct = commentText(lines.get(h)).strip();
                    if (ct.isEmpty()) continue;
                    // Ignore lines made solely of dashes, equals, asterisks, tildes
                    if (ct.matches("^[\\-=*~#\\s]+$")) continue;
                    // Ignore purely descriptive parenthetical comments like "(p-Power/ c-Current/ z-Impedance)"
                    if (ct.startsWith("(") && ct.endsWith(")") && ct.contains("/")) continue;
                    validHeaderLines.add(h);
                }

                List<Integer> dataIdxsEarly = collectDataLines(dataStart, end);
                int expectedTokens = (dataIdxsEarly.isEmpty()) ? 0 : rowTokens(lines.get(dataIdxsEarly.get(0))).size();

                // If multiple candidate header lines exist, check if the last one matches the data token count
                if (validHeaderLines.size() > 1 && expectedTokens > 0) {
                    int lastH = validHeaderLines.get(validHeaderLines.size() - 1);
                    List<String> lastToks = tokenizeHeader(commentText(lines.get(lastH)));
                    if (lastToks.size() == expectedTokens || Math.abs(lastToks.size() - expectedTokens) <= 1) {
                        validHeaderLines = List.of(lastH);
                    }
                }

                for (int h : validHeaderLines) {
                    fieldList.addAll(tokenizeHeader(commentText(lines.get(h))));
                }
            }

            List<Integer> dataIdxs = collectDataLines(dataStart, end);
            if (dataIdxs.isEmpty()) {
                pos = dataStart;
                blockIndex++;
                continue;
            }

            List<String> firstRowTokens = rowTokens(lines.get(dataIdxs.get(0)));

            if (!fieldList.isEmpty() && firstRowTokens.size() >= fieldList.size()) {
                int rIdx = 0;
                for (int lineIdx : dataIdxs) {
                    List<String> toks = rowTokens(lines.get(lineIdx));
                    LinkedHashMap<String, String> fields = new LinkedHashMap<>();
                    for (int i = 0; i < fieldList.size(); i++) {
                        fields.put(fieldList.get(i), i < toks.size() ? toks.get(i) : "");
                    }
                    List<String> key = fieldList.isEmpty() ? List.of() : List.of(fieldList.get(0));
                    DatRecord rec = new DatRecord(section.name, "tabular", fields,
                            blockIndex, rIdx, null, key);
                    section.records.add(rec);
                    rIdx++;
                }
                pos = dataIdxs.get(dataIdxs.size() - 1) + 1;
            } else {
                List<String> allValues = new ArrayList<>();
                List<Integer> consumedIdxs = new ArrayList<>();

                for (int lineIdx : dataIdxs) {
                    List<String> vals = rowTokens(lines.get(lineIdx));

                    if (vals.isEmpty()) {
                        continue;
                    }

                    allValues.addAll(vals);
                    consumedIdxs.add(lineIdx);

                    if (!fieldList.isEmpty() && allValues.size() >= fieldList.size()) {
                        break;
                    }
                }

                LinkedHashMap<String, String> fields = new LinkedHashMap<>();

                if (!fieldList.isEmpty()) {
                    for (int i = 0; i < fieldList.size(); i++) {
                        fields.put(
                                fieldList.get(i),
                                i < allValues.size() ? allValues.get(i) : ""
                        );
                    }
                } else {
                    for (int i = 0; i < allValues.size(); i++) {
                        fields.put("Value" + (i + 1), allValues.get(i));
                    }
                }

                DatRecord rec = new DatRecord(section.name, "subsection", fields,
                        blockIndex, 0, null, List.of());
                section.records.add(rec);
                pos = !consumedIdxs.isEmpty()
                        ? consumedIdxs.get(consumedIdxs.size() - 1) + 1
                        : dataIdxs.get(dataIdxs.size() - 1) + 1;
            }

            blockIndex++;
        }
    }

    /**
     * Formats a double the way it will be re-parsed identically. Note: Java's
     * {@code String.valueOf(double)} doesn't always format identically to
     * Python's {@code str(float)} for the same number (both are "shortest
     * round-trip" style, but the algorithms aren't byte-for-byte identical
     * in every case) -- usually invisible, documented in the project README.
     */
    private static String formatValue(double v) {
        return String.valueOf(v);
    }

    // ------------------------------------------------------------------- //
    private record RowPair(int l1, Integer l2) {
    }

    private record TwoRowInfo(List<String> row1Fields, List<String> row2Fields,
                               List<RowPair> pairs, int lastIdx) {
    }

    private TwoRowInfo tryTwoRow(List<Integer> headerIndices, int dataStart, int end) {
        int row1HeaderIdx = headerIndices.get(headerIndices.size() - 2);
        int row2HeaderIdx = headerIndices.get(headerIndices.size() - 1);
        List<String> row1Fields = tokenizeHeader(commentText(lines.get(row1HeaderIdx)));
        List<String> row2Fields = tokenizeHeader(commentText(lines.get(row2HeaderIdx)));

        if (row1Fields.isEmpty() || row2Fields.isEmpty()) {
            return null;
        }
        if (row2Fields.size() >= row1Fields.size()) {
            return null;
        }

        List<Integer> dataIdxs = collectDataLines(dataStart, end);
        if (dataIdxs.size() < 2) {
            return null;
        }

        List<RowPair> pairs = new ArrayList<>();
        int i = 0;
        while (i < dataIdxs.size()) {
            int l1 = dataIdxs.get(i);
            List<String> toks1 = rowTokens(lines.get(l1));
            if (toks1.size() < row1Fields.size()) {
                return null;
            }
            Integer l2 = null;
            if (i + 1 < dataIdxs.size()) {
                List<String> toks2 = rowTokens(lines.get(dataIdxs.get(i + 1)));
                if (toks2.size() <= row1Fields.size() && toks2.size() >= 1) {
                    l2 = dataIdxs.get(i + 1);
                    i++;
                }
            }
            pairs.add(new RowPair(l1, l2));
            i++;
        }

        if (pairs.isEmpty()) {
            return null;
        }
        boolean allNull = true;
        for (RowPair p : pairs) {
            if (p.l2() != null) {
                allNull = false;
                break;
            }
        }
        if (allNull) {
            return null;
        }

        int lastIdx = dataIdxs.get(dataIdxs.size() - 1);
        return new TwoRowInfo(row1Fields, row2Fields, pairs, lastIdx);
    }

    // ------------------------------------------------------------------- //
    public List<String> getSectionNames() {
        return new ArrayList<>(sections.keySet());
    }

    public DatSection getSection(String name) {
        return sections.get(name);
    }

    public List<DatRecord> getRecords(String sectionName) {
        DatSection section = sections.get(sectionName);
        return (section != null) ? section.records : List.of();
    }

    /**
     * Best-effort detection of this file's MiPower study type, from its raw
     * text and detected section names. Returns a label from
     * {@link Constants#STUDY_TYPE_LABELS}, or {@link Constants#STUDY_UNKNOWN}
     * if nothing matches confidently.
     *
     * <p><b>This is a heuristic</b>, not a read of a guaranteed "study type"
     * field -- a generic, dynamic {@code .dat0} parser like this one has no
     * fixed, canonical location to read that from, because MiPower case
     * files don't reliably carry one. Detection works by looking for
     * terminology characteristic of each study type
     * ({@link Constants#STUDY_TYPE_KEYWORDS}), plus a section-signature
     * fallback for Load Flow specifically
     * ({@link Constants#LFA_SIGNATURE_SECTIONS}). If real files embed a
     * specific, reliable marker, update STUDY_TYPE_KEYWORDS with the exact
     * text and detection becomes exact instead of heuristic. Must be called
     * after {@link #load}/{@link #loadText}.
     */
    public String detectStudyType() {
        String textLower = " " + (rawText == null ? "" : rawText.toLowerCase()) + " ";

        for (String code : Constants.STUDY_TYPE_PRIORITY) {
            for (String keyword : Constants.STUDY_TYPE_KEYWORDS.get(code)) {
                if (textLower.contains(keyword)) {
                    return Constants.STUDY_TYPE_LABELS.get(code);
                }
            }
        }

        Set<String> sectionSet = new HashSet<>(getSectionNames());
        sectionSet.retainAll(Constants.LFA_SIGNATURE_SECTIONS);
        if (sectionSet.size() >= 2) {
            return Constants.STUDY_TYPE_LABELS.get("LFA");
        }

        return Constants.STUDY_UNKNOWN;
    }
}
