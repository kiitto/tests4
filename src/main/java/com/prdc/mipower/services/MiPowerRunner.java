package com.prdc.mipower.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.prdc.mipower.models.RunResult;
import com.prdc.mipower.utils.Constants;
import com.prdc.mipower.utils.FileUtils;

/**
 * Thin, defensive wrapper around MiPower's Load Flow executable
 * ({@code powerlfa.exe}), launched via {@link ProcessBuilder}. Every
 * failure mode (missing output file, missing MiPower install, non-zero
 * exit code, launch failure) is captured in the returned {@link RunResult}
 * instead of throwing -- callers never need a try/catch around a normal
 * "MiPower isn't installed here" outcome.
 */
public final class MiPowerRunner {

    private MiPowerRunner() {
    }

    /**
     * The exact set of files a successful run produces alongside the saved
     * .dat0 itself: OUT0, PLT, ETC, BAR, NT (plus the .dat0 itself, and the
     * "lfa.acd" companion MiPower also writes but which isn't one of the
     * six labeled outputs shown to the user).
     */
    public static Map<String, String> companionOutputPaths(String outputFile) {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("Output File (.dat0)", outputFile);
        paths.put("OUT File (.out0)", FileUtils.withExtension(outputFile, ".out0"));
        paths.put("PLT File (.plt0)", FileUtils.withExtension(outputFile, ".plt0"));
        paths.put("ETC File (.etc0)", FileUtils.withExtension(outputFile, ".etc0"));
        paths.put("BAR File (.bar)", FileUtils.withExtension(outputFile, ".bar"));
        paths.put("NT File (.nt0)", FileUtils.withExtension(outputFile, ".nt0"));
        return paths;
    }

    /** Runs MiPower Load Flow Analysis on the given .dat0 file, blocking until it exits. */
    private static Process launch(String inputFile) throws IOException, InterruptedException {
        String cmdOut = FileUtils.withExtension(inputFile, ".out0");
        String cmdPlt = FileUtils.withExtension(inputFile, ".plt0");
        String cmdEtc = FileUtils.withExtension(inputFile, ".etc0");
        String cmdLfa = Path.of(inputFile).toAbsolutePath().getParent().resolve("lfa.acd").toString();
        String cmdBar = FileUtils.withExtension(inputFile, ".bar");
        String cmdNt = FileUtils.withExtension(inputFile, ".nt0");

        ProcessBuilder pb = new ProcessBuilder(
                Constants.MIPOWER_EXE,
                Constants.MIPOWER_HEADER_TOKEN,
                inputFile,
                cmdOut,
                cmdPlt,
                cmdEtc,
                cmdLfa,
                cmdBar,
                cmdNt
        );
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
        return process;
    }

    /**
     * GUI-facing entry point. Returns a {@link RunResult} -- never throws;
     * every failure mode is captured in the result instead.
     */
    public static RunResult runMiPower(String outputFile) {
        if (outputFile == null || outputFile.isBlank()) {
            return new RunResult(false, "No output file has been saved yet -- click Save first.");
        }
        if (!Files.exists(Path.of(outputFile))) {
            return new RunResult(false, "Output file not found:\n" + outputFile + "\nSave your changes first.");
        }
        if (!Files.exists(Path.of(Constants.MIPOWER_EXE))) {
            return new RunResult(false,
                    "MiPower executable not found at:\n" + Constants.MIPOWER_EXE + "\n"
                            + "This step must be run on the machine where MiPower 10.1 is installed.");
        }

        try {
            Process process = launch(outputFile);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return new RunResult(false, "MiPower exited with an error (code " + exitCode + ").");
            }
            return new RunResult(true, "MiPower Load Flow Analysis completed successfully.",
                    companionOutputPaths(outputFile));
        } catch (IOException e) {
            return new RunResult(false, "Failed to run MiPower: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RunResult(false, "MiPower run was interrupted: " + e.getMessage());
        }
    }
}
