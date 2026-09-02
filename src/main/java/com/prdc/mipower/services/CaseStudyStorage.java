package com.prdc.mipower.services;

import java.io.IOException;
import java.nio.file.Path;

import com.prdc.mipower.models.CaseStudy;
import com.prdc.mipower.utils.FileUtils;

/**
 * PART 6 -- SAVE. Every Case Study gets its own {@code .dat0} file and its
 * own output subfolder; saving one Case Study can never overwrite the Base
 * File, its parent, or a sibling, because every path this class hands out
 * is keyed off {@link CaseStudy#slug()} (which is built from the permanent
 * {@code hierarchicalId}, not the renameable display name).
 *
 * <p>Layout, next to the Base File:
 * <pre>
 * MyProject/
 *   Base.dat0                       (the file the user browsed -- untouched)
 *   CaseStudies/
 *     Case1/
 *       Case1.dat0
 *       Outputs/
 *         Case1.out0  Case1.bar  Case1.plt0  Case1.etc0  Case1.nt0
 *     Case1_1/
 *       Case1_1.dat0
 *       Outputs/
 *         Case1_1.out0  ...
 *     Case2/
 *       Case2.dat0
 *       Outputs/
 *         Case2.out0  ...
 * </pre>
 */
public class CaseStudyStorage {

    private final String workingDir;

    public CaseStudyStorage(String baseFilePath) {
        this.workingDir = FileUtils.parentDir(baseFilePath);
    }

    private Path caseDir(CaseStudy cs) {
        return Path.of(workingDir, "CaseStudies", cs.slug());
    }

    /** Where this Case Study's own {@code .dat0} snapshot lives -- never the Base File's path. */
    public String dat0Path(CaseStudy cs) {
        return caseDir(cs).resolve(cs.slug() + ".dat0").toString();
    }

    /** Where MiPower should be pointed to run THIS Case Study (its own saved .dat0). */
    public String outputsDir(CaseStudy cs) {
        return caseDir(cs).resolve("Outputs").toString();
    }

    /**
     * MiPower writes companion output files (.out0/.bar/.plt0/.etc0/.nt0)
     * next to the .dat0 it was pointed at, using the SAME base filename.
     * So "the .dat0 to run" doubles as "where the outputs will land" --
     * this returns the path MiPowerRunner should be given.
     */
    public String runnableDat0Path(CaseStudy cs) {
        return outputsDir(cs) + java.io.File.separator + cs.slug() + ".dat0";
    }

    /** Writes {@code resolvedText} to this Case Study's own file, creating parent directories as needed. */
    public String save(CaseStudy cs, String resolvedText) throws IOException {
        String path = dat0Path(cs);
        FileUtils.ensureParentDirs(path);
        FileUtils.writeText(path, resolvedText);

        // Also stage an identical copy where Run MiPower will point, so
        // companion output files land in a predictable per-Case-Study
        // folder instead of next to the Base File.
        String runnable = runnableDat0Path(cs);
        FileUtils.ensureParentDirs(runnable);
        FileUtils.writeText(runnable, resolvedText);
        return path;
    }
}
