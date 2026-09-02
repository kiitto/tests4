package com.prdc.mipower.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The outcome of one MiPower execution attempt (via the future
 * {@code MiPowerRunner} service). Two things live here:
 * <ul>
 *   <li>the raw pass/fail outcome of a single {@code runMiPower()} call
 *       (success, message, output file paths) -- see
 *       {@link #RunResult(boolean, String, Map)}</li>
 *   <li>once appended to a {@link CaseStudy}'s run history, also carries
 *       which Case Study it belongs to and how long it took, for Run
 *       History / Analytics display.</li>
 * </ul>
 */
public class RunResult {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public final boolean success;
    public final String message;
    public final Map<String, String> outputFiles;

    // Populated when this RunResult is recorded into a Case Study's history
    // (see CaseStudy.recordRun() in the services module); null/0 until then.
    public String caseStudyName;
    public double executionSeconds;
    public String timestamp;

    public RunResult(boolean success, String message, Map<String, String> outputFiles) {
        this.success = success;
        this.message = message;
        this.outputFiles = (outputFiles != null) ? outputFiles : new LinkedHashMap<>();
        this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    public RunResult(boolean success, String message) {
        this(success, message, null);
    }

    public String statusText() {
        return success ? "Executed Successfully" : "Execution Failed";
    }

    @Override
    public String toString() {
        return "RunResult(success=" + success + ", message=" + message + ")";
    }
}
