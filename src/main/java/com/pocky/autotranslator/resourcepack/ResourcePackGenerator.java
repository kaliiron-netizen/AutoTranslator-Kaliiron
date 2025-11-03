package com.pocky.autotranslator.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pocky.autotranslator.AutoTranslatorMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates a resource pack with translated language files.
 */
public class ResourcePackGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PACK_FORMAT = 34; // Minecraft 1.21 pack format

    private final Path resourcePackPath;
    private final String targetLanguage;
    private final String targetLanguageFile;

    public ResourcePackGenerator(Path gameDirectory, String targetLanguage) {
        this.resourcePackPath = gameDirectory.resolve("resourcepacks").resolve("AutoTranslator");
        this.targetLanguage = targetLanguage;
        // Convert "ru" to "ru_ru" for Minecraft language file format
        this.targetLanguageFile = targetLanguage + "_" + targetLanguage;
    }

    /**
     * Generates a resource pack with all translations.
     *
     * @param translations Map of namespace -> (translation key -> translated value)
     */
    public void generateResourcePack(Map<String, Map<String, String>> translations) throws IOException {
        AutoTranslatorMod.LOGGER.info("Generating resource pack at: {}", resourcePackPath);

        // Create resource pack directory
        Files.createDirectories(resourcePackPath);

        // Generate pack.mcmeta
        generatePackMcmeta();

        // Generate language files for each namespace
        for (Map.Entry<String, Map<String, String>> entry : translations.entrySet()) {
            String namespace = entry.getKey();
            Map<String, String> namespaceTranslations = entry.getValue();

            if (!namespaceTranslations.isEmpty()) {
                generateLanguageFile(namespace, namespaceTranslations);
            }
        }

        AutoTranslatorMod.LOGGER.info("Resource pack generation complete!");
    }

    /**
     * Generates the pack.mcmeta file for the resource pack.
     */
    private void generatePackMcmeta() throws IOException {
        JsonObject packMeta = new JsonObject();
        JsonObject pack = new JsonObject();

        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Auto-generated translations by AutoTranslator");

        packMeta.add("pack", pack);

        Path metaFile = resourcePackPath.resolve("pack.mcmeta");
        Files.writeString(metaFile, GSON.toJson(packMeta));

        AutoTranslatorMod.LOGGER.debug("Generated pack.mcmeta");
    }

    /**
     * Generates a language file for a specific namespace.
     *
     * @param namespace    The mod namespace
     * @param translations Map of translation key -> translated value
     */
    private void generateLanguageFile(String namespace, Map<String, String> translations) throws IOException {
        // Create directory structure: assets/<namespace>/lang/
        Path langDir = resourcePackPath.resolve("assets").resolve(namespace).resolve("lang");
        Files.createDirectories(langDir);

        // Create language file (e.g., ru_ru.json)
        Path langFile = langDir.resolve(targetLanguageFile + ".json");

        // Convert translations to JSON
        JsonObject translationJson = new JsonObject();
        translations.forEach(translationJson::addProperty);

        // Write to file
        Files.writeString(langFile, GSON.toJson(translationJson));

        AutoTranslatorMod.LOGGER.info("Generated language file for namespace '{}' with {} translations",
                namespace, translations.size());
    }

    /**
     * Checks if the resource pack already exists.
     */
    public boolean resourcePackExists() {
        return Files.exists(resourcePackPath) && Files.isDirectory(resourcePackPath);
    }

    /**
     * Deletes the existing resource pack.
     */
    public void deleteResourcePack() throws IOException {
        if (resourcePackExists()) {
            deleteDirectory(resourcePackPath);
            AutoTranslatorMod.LOGGER.info("Deleted existing resource pack");
        }
    }

    /**
     * Recursively deletes a directory.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            AutoTranslatorMod.LOGGER.error("Failed to delete: {}", path, e);
                        }
                    });
        }
    }

    /**
     * Gets the resource pack directory path.
     */
    public Path getResourcePackPath() {
        return resourcePackPath;
    }
}
