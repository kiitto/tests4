package com.prdc.mipower.parser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.prdc.mipower.models.Bus;
import com.prdc.mipower.models.Line;
import com.prdc.mipower.models.Out0Results;
import com.prdc.mipower.models.Transformer;
import com.prdc.mipower.utils.FileUtils;

/**
 * Parses the result sections of a MiPower Load Flow {@code .out0} file.
 * Deliberately kept separate from {@link DatParser} (which only ever
 * handles {@code .dat0} input files) -- solved-result parsing is a
 * different concern with a different (fixed, known) shape, exposed here as
 * plain {@link com.prdc.mipower.models.Bus}/{@link Line}/{@link Transformer}
 * models. Tolerant of MiPower's decorative separators and loading symbols,
 * and of Fortran-style "D"/"d" exponent notation (e.g. "1.234D+02").
 */
public final class Out0Parser {

    private Out0Parser() {
    }

    private static final Pattern ITERATIONS_PATTERN = Pattern.compile(
            "Number of P Iterations\\s*:\\s*(\\d+)\\s+and Number of Q Iterations\\s*:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_BOUNDARY = Pattern.compile("\\n-{30,}|\\|\\*{5}");

    private static final Pattern BRANCH_ROW = Pattern.compile(
            "^\\s*(\\d+)\\s+\\d+\\s+(\\d+)\\s+(\\S+)\\s+(\\d+)\\s+(\\S+)\\s+"
                    + "([-+0-9.EeDd]+)\\s+([-+0-9.EeDd]+)\\s+([-+0-9.EeDd]+)\\s+"
                    + "([-+0-9.EeDd]+)\\s+([-+0-9.EeDd]+)\\s*([^\\s\\d]?)\\s*$");

    private static final Pattern BUS_ROW = Pattern.compile(
            "^\\s*(\\d+)\\s+(\\S+)\\s+([-+0-9.EeDd]+)\\s+([-+0-9.EeDd]+)\\s+"
                    + "([-+0-9.EeDd]+)\\s+([-+0-9.EeDd]+)\\s+([-+0-9.EeDd]+)\\s+"
                    + "([-+0-9.EeDd]+)\\s+[-+0-9.EeDd]+\\s*([^\\s\\d]*)");

    /** MiPower's Fortran-style "D"/"d" exponent notation -&gt; standard "E"/"e" before parsing. */
    private static double parseFortranFloat(String value) {
        return Double.parseDouble(value.replace("D", "E").replace("d", "e"));
    }

    /**
     * Splits text into lines the way Python's {@code str.splitlines()}
     * does -- tolerant of "\r\n", bare "\r", and bare "\n" -- rather than a
     * naive {@code split("\n")}, which would leave a stray trailing "\r" on
     * every line if the source file uses Windows line endings (likely,
     * since MiPower itself is Windows software).
     */
    private static String[] splitLines(String text) {
        if (text.isEmpty()) {
            return new String[0];
        }
        return text.split("\r\n|\r|\n", -1);
    }

    /**
     * Finds the text of one titled section, from just after {@code title}
     * (case-insensitive) up to the next decorative boundary line (30+
     * dashes, or "|" followed by 5 asterisks), or to the end of the text if
     * no such boundary follows.
     */
    private static String section(String text, String title) {
        Pattern titlePattern = Pattern.compile(Pattern.quote(title), Pattern.CASE_INSENSITIVE);
        Matcher m = titlePattern.matcher(text);
        if (!m.find()) {
            return "";
        }
        String remainder = text.substring(m.end());
        Matcher boundary = SECTION_BOUNDARY.matcher(remainder);
        if (boundary.find()) {
            return remainder.substring(0, boundary.start());
        }
        return remainder;
    }

    private static Double summaryValue(String text, String label) {
        Pattern p = Pattern.compile(Pattern.quote(label) + "\\s*:\\s*([-+0-9.EeDd]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return parseFortranFloat(m.group(1));
        }
        return null;
    }

    private static void parseBranchesInto(String block, String kind, Out0Results result) {
        for (String line : splitLines(block)) {
            Matcher m = BRANCH_ROW.matcher(line);
            if (!m.matches()) {
                continue;
            }
            int number = Integer.parseInt(m.group(1));
            int fromBus = Integer.parseInt(m.group(2));
            String fromName = m.group(3);
            int toBus = Integer.parseInt(m.group(4));
            String toName = m.group(5);
            double mwFlow = parseFortranFloat(m.group(6));
            double mvarFlow = parseFortranFloat(m.group(7));
            double mwLoss = parseFortranFloat(m.group(8));
            double mvarLoss = parseFortranFloat(m.group(9));
            double loadingPercent = parseFortranFloat(m.group(10));
            String loadingBand = m.group(11).strip();

            if ("Transformer".equals(kind)) {
                result.transformers.add(new Transformer(number, fromBus, fromName, toBus, toName,
                        mwFlow, mvarFlow, mwLoss, mvarLoss, loadingPercent, loadingBand));
            } else {
                result.lines.add(new Line(number, fromBus, fromName, toBus, toName,
                        mwFlow, mvarFlow, mwLoss, mvarLoss, loadingPercent, loadingBand));
            }
        }
    }

    /** Reads a file and returns structured Load Flow results, or throws Out0ParseError/IOException. */
    public static Out0Results parseFile(String filePath) throws IOException, Out0ParseError {
        String text = FileUtils.readText(filePath);
        return parseText(text, filePath);
    }

    /** Parses already-loaded text (useful for tests, or re-parsing without touching disk again). */
    public static Out0Results parseText(String text, String sourcePath) throws Out0ParseError {
        String upper = text.toUpperCase();
        if (!upper.contains("OUTPUT RESULTS") || !upper.contains("LOAD FLOW")) {
            throw new Out0ParseError("This does not appear to be a MiPower Load Flow .out0 file.");
        }

        Out0Results result = new Out0Results(sourcePath);

        Matcher iterMatch = ITERATIONS_PATTERN.matcher(text);
        if (iterMatch.find()) {
            result.pIterations = Integer.parseInt(iterMatch.group(1));
            result.qIterations = Integer.parseInt(iterMatch.group(2));
            result.converged = true;
        }

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("real_generation_mw", "TOTAL REAL POWER GENERATION (CONVENTIONAL)");
        labels.put("reactive_generation_mvar", "TOTAL REACT. POWER GENERATION (CONVENTIONAL)");
        labels.put("real_load_mw", "TOTAL REAL POWER LOAD");
        labels.put("reactive_load_mvar", "TOTAL REACTIVE POWER LOAD");
        labels.put("real_loss_mw", "TOTAL REAL POWER LOSS (AC+DC)");
        labels.put("real_loss_percent", "PERCENTAGE REAL  LOSS (AC+DC)");
        labels.put("reactive_loss_mvar", "TOTAL REACTIVE POWER  LOSS");

        for (Map.Entry<String, String> e : labels.entrySet()) {
            Double value = summaryValue(text, e.getValue());
            if (value != null) {
                result.summary.put(e.getKey(), value);
            }
        }

        String busBlock = section(text, "BUS VOLTAGES AND POWERS");
        for (String line : splitLines(busBlock)) {
            Matcher m = BUS_ROW.matcher(line);
            // NOTE: BUS_ROW has no trailing $ anchor (unlike BRANCH_ROW) --
            // the original Python uses re.match(), which only requires a
            // PREFIX match, so trailing content on the line (there is some,
            // in real files) is fine and ignored. lookingAt() is Java's
            // equivalent of Python's re.match(); matches() would wrongly
            // require the entire line to match and reject valid rows.
            if (!m.lookingAt()) {
                continue;
            }
            int number = Integer.parseInt(m.group(1));
            String name = m.group(2);
            double voltagePu = parseFortranFloat(m.group(3));
            double angleDeg = parseFortranFloat(m.group(4));
            double mwGeneration = parseFortranFloat(m.group(5));
            double mvarGeneration = parseFortranFloat(m.group(6));
            double mwLoad = parseFortranFloat(m.group(7));
            double mvarLoad = parseFortranFloat(m.group(8));
            String voltageFlag = m.group(9);
            result.buses.add(new Bus(number, name, voltagePu, angleDeg,
                    mwGeneration, mvarGeneration, mwLoad, mvarLoad, voltageFlag));
        }
        for (Bus bus : result.buses) {
            if (bus.isBelowMinVoltage()) {
                result.voltageMinViolations++;
            }
            if (bus.isAboveMaxVoltage()) {
                result.voltageMaxViolations++;
            }
        }

        parseBranchesInto(section(text, "TRANSFORMER FLOWS AND TRANSFORMER LOSSES"), "Transformer", result);
        parseBranchesInto(section(text, "LINE FLOWS AND LINE LOSSES"), "Line", result);

        return result;
    }
}
