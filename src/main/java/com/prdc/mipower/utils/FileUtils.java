package com.prdc.mipower.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralized file I/O so no service/GUI class talks to {@link Files}
 * directly -- one place to change encoding, error handling, or path
 * derivation rules (e.g. the "Input.dat0" -&gt; "Input_Modified_CaseN.dat0"
 * naming) later.
 */
public final class FileUtils {

    private FileUtils() {
    }

    public static String readText(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    public static void writeText(String path, String content) throws IOException {
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
    }

    public static boolean exists(String path) {
        return path != null && Files.exists(Path.of(path));
    }

    public static String baseName(String path) {
        return Path.of(path).getFileName().toString();
    }

    public static String parentDir(String path) {
        Path parent = Path.of(path).toAbsolutePath().getParent();
        return (parent != null) ? parent.toString() : ".";
    }

    /** "C:/x/Input.dat0" -&gt; "C:/x/Input_Modified_Case2.dat0". */
    public static String deriveModifiedPath(String inputPath, String caseSlug) {
        String base = inputPath.toLowerCase().endsWith(".dat0")
                ? inputPath.substring(0, inputPath.length() - 5)
                : inputPath;
        String suffix = (caseSlug == null || caseSlug.isBlank()) ? "" : ("_" + caseSlug);
        return base + "_Modified" + suffix + ".dat0";
    }

    /** Same ".dat0"-&gt;other-extension naming MiPower itself uses for companion files. */
    public static String withExtension(String dat0Path, String newExtension) {
        if (dat0Path.toLowerCase().endsWith(".dat0")) {
            return dat0Path.substring(0, dat0Path.length() - 5) + newExtension;
        }
        return dat0Path + newExtension;
    }

    /** Report filename for a given Case Study slug, in the same folder as outputPath. */
    public static String deriveReportPath(String outputPath, String caseSlug, String extension) {
        String dir = parentDir(outputPath);
        String suffix = (caseSlug == null || caseSlug.isBlank()) ? "" : ("_" + caseSlug);
        return Path.of(dir, "Changes_Report" + suffix + extension).toString();
    }

    public static void ensureParentDirs(String path) throws IOException {
        Path parent = Path.of(path).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
