package com.prdc.mipower.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Persists the Dashboard's recently-opened {@code .dat0} file list to a
 * small JSON file in the user's home directory, via Jackson. Deliberately
 * small and focused -- this is the only thing in the project that needs
 * JSON persistence so far (Case Studies and their history are all
 * in-memory/session-only, matching the Python original's behavior).
 */
public class RecentFilesService {

    private static final int MAX_ENTRIES = 10;
    private final Path storePath;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public RecentFilesService() {
        this(Path.of(System.getProperty("user.home"), ".prdc_mipower_editor", "recent_files.json"));
    }

    /** Package/test-visible constructor for pointing at a custom location. */
    public RecentFilesService(Path storePath) {
        this.storePath = storePath;
    }

    public List<String> load() {
        try {
            File file = storePath.toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            String[] paths = mapper.readValue(file, String[].class);
            return new ArrayList<>(List.of(paths));
        } catch (IOException e) {
            // A corrupt/unreadable recent-files list should never block the
            // app from starting -- just start with an empty list.
            return new ArrayList<>();
        }
    }

    public void addRecent(String path) {
        List<String> current = load();
        current.remove(path);
        current.add(0, path);
        while (current.size() > MAX_ENTRIES) {
            current.remove(current.size() - 1);
        }
        save(current);
    }

    private void save(List<String> paths) {
        try {
            File file = storePath.toFile();
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            mapper.writeValue(file, paths);
        } catch (IOException e) {
            // Non-fatal: recent files is a convenience, not core functionality.
            System.out.println("Warning: could not save recent files list: " + e.getMessage());
        }
    }
}
