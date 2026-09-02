package com.prdc.mipower.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import com.prdc.mipower.utils.Constants;

/**
 * Everything {@code Out0Parser} extracts from one solved MiPower Load Flow
 * {@code .out0} file. Not explicitly in the requested model list, but
 * necessary as the container {@code Out0Parser} returns -- mirrors the
 * Python project's {@code out0_parser.Out0Results} dataclass, including its
 * derived {@code analysis_metrics()} aggregations (done here with plain
 * Java streams rather than a pandas equivalent, since none was requested
 * and streams are the natural fit).
 */
public class Out0Results {

    public final String path;
    public boolean converged;
    public Integer pIterations;
    public Integer qIterations;
    public Map<String, Double> summary = new LinkedHashMap<>();
    public List<Bus> buses = new ArrayList<>();
    public List<Transformer> transformers = new ArrayList<>();
    public List<Line> lines = new ArrayList<>();
    public int voltageMinViolations;
    public int voltageMaxViolations;

    public Out0Results(String path) {
        this.path = path;
    }

    /** Every branch (lines + transformers) as one combined view. */
    public List<Branch> branches() {
        List<Branch> all = new ArrayList<>();
        all.addAll(transformers);
        all.addAll(lines);
        return all;
    }

    public record AnalysisMetrics(
            Double minimumVoltagePu, Double maximumVoltagePu, Double averageVoltagePu, Double voltageStdPu,
            Double maximumLoadingPercent, Double averageLoadingPercent,
            int overloadedBranches, int highLoadedBranches, Double powerBalanceMw) {
    }

    /** Derived engineering metrics -- min/max/avg/std voltage, loading stats, power balance. */
    public AnalysisMetrics analysisMetrics() {
        double[] voltages = buses.stream().mapToDouble(b -> b.voltagePu).toArray();
        double[] loadings = branches().stream().mapToDouble(b -> b.loadingPercent).toArray();

        Double minV = voltages.length > 0 ? min(voltages) : null;
        Double maxV = voltages.length > 0 ? max(voltages) : null;
        Double avgV = voltages.length > 0 ? mean(voltages) : null;
        Double stdV = voltages.length > 0 ? stdDev(voltages) : null;

        Double maxL = loadings.length > 0 ? max(loadings) : null;
        Double avgL = loadings.length > 0 ? mean(loadings) : null;

        int overloaded = (int) java.util.Arrays.stream(loadings)
                .filter(v -> v >= Constants.OVERLOAD_THRESHOLD_PCT).count();
        int highLoaded = (int) java.util.Arrays.stream(loadings)
                .filter(v -> v >= Constants.HIGH_LOADING_THRESHOLD_PCT && v < Constants.OVERLOAD_THRESHOLD_PCT)
                .count();

        Double generated = summary.get("real_generation_mw");
        Double load = summary.get("real_load_mw");
        Double loss = summary.get("real_loss_mw");
        Double powerBalance = (generated != null && load != null && loss != null)
                ? (generated - load - loss) : null;

        return new AnalysisMetrics(minV, maxV, avgV, stdV, maxL, avgL, overloaded, highLoaded, powerBalance);
    }

    private static double min(double[] a) {
        return java.util.Arrays.stream(a).min().orElse(0);
    }

    private static double max(double[] a) {
        return java.util.Arrays.stream(a).max().orElse(0);
    }

    private static double mean(double[] a) {
        OptionalDouble avg = java.util.Arrays.stream(a).average();
        return avg.isPresent() ? avg.getAsDouble() : 0;
    }

    /** Population standard deviation (ddof=0), matching the Python version's pandas std(ddof=0). */
    private static double stdDev(double[] a) {
        double m = mean(a);
        double sumSquares = 0;
        for (double v : a) {
            sumSquares += (v - m) * (v - m);
        }
        return Math.sqrt(sumSquares / a.length);
    }
}
