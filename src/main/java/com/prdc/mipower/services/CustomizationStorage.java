package com.prdc.mipower.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.prdc.mipower.models.SavedCustomization;

/**
 * Manages persisting and loading user Saved Customizations.
 */
public class CustomizationStorage {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path STORAGE_DIR = Paths.get(
            System.getProperty("user.home"), ".gemini", "antigravity", "mipower_customizations");
    private static final Path STORAGE_FILE = STORAGE_DIR.resolve("saved_customizations.json");

    public static synchronized List<SavedCustomization> loadAll() {
        if (!Files.exists(STORAGE_FILE)) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(STORAGE_FILE.toFile(), new TypeReference<List<SavedCustomization>>() {});
        } catch (Exception e) {
            System.err.println("Failed to load customizations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static synchronized boolean save(SavedCustomization customization) {
        if (customization == null) return false;
        try {
            Files.createDirectories(STORAGE_DIR);
            List<SavedCustomization> all = loadAll();
            all.removeIf(c -> c.id != null && c.id.equals(customization.id));
            all.add(0, customization);
            MAPPER.writeValue(STORAGE_FILE.toFile(), all);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to save customization: " + e.getMessage());
            return false;
        }
    }

    public static synchronized boolean delete(String id) {
        if (id == null) return false;
        try {
            List<SavedCustomization> all = loadAll();
            boolean removed = all.removeIf(c -> id.equals(c.id));
            if (removed) {
                Files.createDirectories(STORAGE_DIR);
                MAPPER.writeValue(STORAGE_FILE.toFile(), all);
            }
            return removed;
        } catch (IOException e) {
            System.err.println("Failed to delete customization: " + e.getMessage());
            return false;
        }
    }
}

