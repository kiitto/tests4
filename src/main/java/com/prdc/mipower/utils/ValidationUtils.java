package com.prdc.mipower.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two things live here, both "no editing/parsing logic should have to
 * reimplement this" utilities:
 * <ol>
 *   <li>Value type detection/validation (Integer/Float/Scientific/Text) for
 *       the table editor.</li>
 *   <li>Low-level text tokenizing helpers shared by {@code DatParser} and
 *       the modification-writing service -- so what the parser *detects*
 *       and what gets *written back* always agree on field boundaries.</li>
 * </ol>
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    // --------------------------------------------------------------------- //
    // Value type detection / validation
    // --------------------------------------------------------------------- //
    public static final String INTEGER = "Integer";
    public static final String FLOAT = "Float";
    public static final String SCIENTIFIC = "Scientific";
    public static final String TEXT = "Text";

    private static final Pattern INT_RE = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern FLOAT_RE = Pattern.compile("^[+-]?\\d+\\.\\d+$");
    private static final Pattern SCI_RE = Pattern.compile("^[+-]?\\d+(\\.\\d+)?[eE][+-]?\\d+$");

    /** Detects the type of a token as it appears in a MiPower .dat0 file. */
    public static String detectType(String value) {
        if (value == null) {
            return TEXT;
        }
        String token = value.strip();
        if (token.isEmpty()) {
            return TEXT;
        }
        if (SCI_RE.matcher(token).matches()) {
            return SCIENTIFIC;
        }
        if (FLOAT_RE.matcher(token).matches()) {
            return FLOAT;
        }
        if (INT_RE.matcher(token).matches()) {
            return INTEGER;
        }
        return TEXT;
    }

    public static String allowedFormat(String detectedType) {
        return switch (detectedType) {
            case INTEGER -> "Whole number, e.g. 12 or -4";
            case FLOAT -> "Decimal number, e.g. 1.05 or -0.30";
            case SCIENTIFIC -> "Scientific notation, e.g. 1.00000e-004";
            case TEXT -> "Free text (letters/numbers allowed)";
            default -> "Free text";
        };
    }

    public record ValidationResult(boolean valid, String message) {
    }

    /** Validates a proposed new value against the detected type of the field being edited. */
    public static ValidationResult validateValue(String newValue, String detectedType) {
        String token = (newValue == null) ? "" : newValue.strip();

        if (token.isEmpty()) {
            return new ValidationResult(false, "Value cannot be empty.");
        }
        if (TEXT.equals(detectedType)) {
            return new ValidationResult(true, "OK");
        }
        if (tryParsePyFloat(token) == null) {
            return new ValidationResult(false,
                    "'" + newValue + "' is not a valid number (" + allowedFormat(detectedType) + ").");
        }
        if (INTEGER.equals(detectedType)) {
            if (!INT_RE.matcher(token).matches() && !SCI_RE.matcher(token).matches()) {
                double d = tryParsePyFloat(token);
                if (d != (long) d) {
                    return new ValidationResult(false, "'" + newValue + "' is not a whole number.");
                }
            }
            return new ValidationResult(true, "OK");
        }
        return new ValidationResult(true, "OK");
    }

    // --------------------------------------------------------------------- //
    // Numeric parsing matching Python float() semantics exactly
    // --------------------------------------------------------------------- //
    // Java's Double.parseDouble accepts a trailing d/D/f/F suffix (e.g.
    // "1.5d") that Python's float() rejects. A MiPower token ending in one
    // of those letters would otherwise be mis-detected as numeric.
    private static final Pattern PY_FLOAT = Pattern.compile(
            "^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$");

    public static Double tryParsePyFloat(String token) {
        if (token == null) {
            return null;
        }
        String t = token.strip();
        if (t.isEmpty() || !PY_FLOAT.matcher(t).matches()) {
            return null;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --------------------------------------------------------------------- //
    // Text tokenizing helpers (shared by DatParser and the modifier service)
    // --------------------------------------------------------------------- //
    public static boolean isCommentLine(String line) {
        return line.strip().startsWith("%");
    }

    /** Text of a '%...' line with the leading %'s stripped. */
    public static String commentText(String line) {
        return lstripPercent(line.strip()).strip();
    }

    public static String lstripPercent(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '%') {
            i++;
        }
        return s.substring(i);
    }

    public static String rstripChars(String s, String chars) {
        int end = s.length();
        while (end > 0 && chars.indexOf(s.charAt(end - 1)) >= 0) {
            end--;
        }
        return s.substring(0, end);
    }

    private static List<String> whitespaceSplit(String text) {
        String t = text.strip();
        if (t.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(t.split("\\s+")));
    }

    /**
     * Splits a header/comment line into field-name tokens on whitespace,
     * keeping parenthetical qualifiers such as "(pu)" merged into the
     * preceding token rather than letting an internal space split it in two.
     */
    public static List<String> tokenizeHeader(String text) {
        List<String> rawTokens = whitespaceSplit(text);
        List<String> tokens = new ArrayList<>();
        StringBuilder buffer = null;
        int depth = 0;

        for (String tok : rawTokens) {
            if (buffer == null) {
                buffer = new StringBuilder(tok);
            } else {
                buffer.append(' ').append(tok);
            }
            depth += countChar(tok, '(') - countChar(tok, ')');
            if (depth <= 0) {
                tokens.add(buffer.toString());
                buffer = null;
                depth = 0;
            }
        }
        if (buffer != null) {
            tokens.add(buffer.toString());
        }
        return tokens;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    public static List<String> rowTokens(String line) {
        return whitespaceSplit(line);
    }

    public static List<Double> extractNumericValues(String line) {
        List<Double> values = new ArrayList<>();
        for (String token : rowTokens(line)) {
            Double v = tryParsePyFloat(token);
            if (v != null) {
                values.add(v);
            }
        }
        return values;
    }

    public static boolean valuesEqual(Object a, Object b) {
        Double da = (a == null) ? null : tryParsePyFloat(String.valueOf(a));
        Double db = (b == null) ? null : tryParsePyFloat(String.valueOf(b));
        if (da != null && db != null) {
            return da.doubleValue() == db.doubleValue();
        }
        String sa = (a == null) ? "" : String.valueOf(a).strip();
        String sb = (b == null) ? "" : String.valueOf(b).strip();
        return sa.equals(sb);
    }

    /** Resolves a real header field name to its 0-based column position. */
    public static int findFieldIndex(String fieldName, List<String> headerFields) {
        String target = fieldName.strip().toLowerCase();
        for (int i = 0; i < headerFields.size(); i++) {
            if (headerFields.get(i).toLowerCase().equals(target)) {
                return i;
            }
        }
        for (int i = 0; i < headerFields.size(); i++) {
            if (headerFields.get(i).toLowerCase().startsWith(target)) {
                return i;
            }
        }
        for (int i = 0; i < headerFields.size(); i++) {
            if (headerFields.get(i).toLowerCase().contains(target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * If {@code token} looks like a numbered-field marker ("6.Base", "21",
     * "22:", "8"), returns its (number, glued-name-remainder). Otherwise
     * {@code null}. A digit run followed by '-' (e.g. "0-NonScalable)") is
     * deliberately rejected -- that's an option-value description, not a
     * field marker.
     */
    private static final Pattern MARKER = Pattern.compile("^(\\d+)(.*)$");

    public record MarkerSplit(int number, String rest) {
    }

    public static MarkerSplit markerSplit(String token) {
        Matcher m = MARKER.matcher(token);
        if (!m.matches()) {
            return null;
        }
        int num;
        try {
            num = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String rest = m.group(2);
        if (rest.isEmpty()) {
            return new MarkerSplit(num, "");
        }
        char c0 = rest.charAt(0);
        if (c0 == '.' || c0 == ':') {
            return new MarkerSplit(num, rest.substring(1));
        }
        if (Character.isLetter(c0)) {
            return new MarkerSplit(num, rest);
        }
        return null;
    }

    /**
     * Parses "numbered parameter" comment blocks, e.g.
     * "6.Base MVA   7.Nominal Frequency" (separators after the digits are
     * sometimes '.', sometimes ':', occasionally missing) into an ordered
     * {number -&gt; field name} map. One physical header line at a time: a
     * field's name is only ever built from tokens on the SAME line as its
     * marker, so option-description continuation lines with no marker of
     * their own are simply skipped rather than appended to the previous
     * field's name.
     */
    public static LinkedHashMap<Integer, String> buildNumberedFieldMap(List<String> headerLinesText) {
        LinkedHashMap<Integer, String> mapping = new LinkedHashMap<>();

        for (String rawLine : headerLinesText) {
            List<String> tokens = whitespaceSplit(lstripPercent(rawLine));
            int i = 0;
            int n = tokens.size();
            while (i < n) {
                MarkerSplit split = markerSplit(tokens.get(i));
                if (split == null) {
                    i++;
                    continue;
                }
                List<String> nameParts = new ArrayList<>();
                if (!split.rest().isEmpty()) {
                    nameParts.add(split.rest());
                }
                i++;
                while (i < n && markerSplit(tokens.get(i)) == null) {
                    nameParts.add(tokens.get(i));
                    i++;
                }
                String name = String.join(" ", nameParts).replaceAll("\\s+", " ").strip();
                name = rstripChars(name, ".,;:").strip();
                if (!name.isEmpty()) {
                    mapping.put(split.number(), name);
                }
            }
        }
        return mapping;
    }

    public static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase();
    }
}
