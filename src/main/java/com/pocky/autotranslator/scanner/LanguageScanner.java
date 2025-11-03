package com.pocky.autotranslator.scanner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pocky.autotranslator.AutoTranslatorMod;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Scans all loaded mods for language files and detects missing translations.
 */
public class LanguageScanner {
    private static final Gson GSON = new Gson();
    private final String sourceLanguage;
    private final String targetLanguage;

    public LanguageScanner(String sourceLanguage, String targetLanguage) {
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Scans all mods and returns a map of namespace -> missing translation keys.
     *
     * @param packResources All available pack resources
     * @return Map of mod namespace to map of translation keys and English values
     */
    public Map<String, Map<String, String>> scanForMissingTranslations(Iterable<PackResources> packResources) {
        Map<String, Map<String, String>> missingTranslations = new HashMap<>();

        for (PackResources pack : packResources) {
            String packId = pack.packId();
            AutoTranslatorMod.LOGGER.debug("Scanning pack: {}", packId);

            // Get all namespaces in this pack
            Set<String> namespaces = pack.getNamespaces(PackType.CLIENT_RESOURCES);

            for (String namespace : namespaces) {
                Map<String, String> missing = scanNamespace(pack, namespace);
                if (!missing.isEmpty()) {
                    AutoTranslatorMod.LOGGER.info("Found {} missing translations for namespace: {}",
                            missing.size(), namespace);
                    missingTranslations.put(namespace, missing);
                }
            }
        }

        return missingTranslations;
    }

    /**
     * Scans a specific namespace for missing translations.
     */
    private Map<String, String> scanNamespace(PackResources pack, String namespace) {
        Map<String, String> missingTranslations = new HashMap<>();

        try {
            // Load English translations (source)
            Map<String, String> englishTranslations = loadLanguageFile(pack, namespace, sourceLanguage);
            if (englishTranslations.isEmpty()) {
                return missingTranslations;
            }

            // Load target language translations (if exists)
            Map<String, String> targetTranslations = loadLanguageFile(pack, namespace, targetLanguage);

            // Find missing keys
            for (Map.Entry<String, String> entry : englishTranslations.entrySet()) {
                String key = entry.getKey();
                if (!targetTranslations.containsKey(key)) {
                    missingTranslations.put(key, entry.getValue());
                }
            }

        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Error scanning namespace: {}", namespace, e);
        }

        return missingTranslations;
    }

    /**
     * Loads a language file from a resource pack.
     *
     * @param pack      The resource pack
     * @param namespace The mod namespace
     * @param language  Language code (e.g., "en_us", "ru_ru")
     * @return Map of translation keys to values
     */
    private Map<String, String> loadLanguageFile(PackResources pack, String namespace, String language) {
        Map<String, String> translations = new HashMap<>();

        // Create ResourceLocation for the language file
        net.minecraft.resources.ResourceLocation location =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, "lang/" + language + ".json");

        try {
            IoSupplier<InputStream> resource = pack.getResource(PackType.CLIENT_RESOURCES, location);
            if (resource == null) {
                return translations;
            }

            try (InputStream inputStream = resource.get();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
                if (jsonObject != null) {
                    jsonObject.entrySet().forEach(entry ->
                            translations.put(entry.getKey(), entry.getValue().getAsString())
                    );
                }
            }

        } catch (IOException e) {
            // File doesn't exist - this is expected for missing translations
            AutoTranslatorMod.LOGGER.debug("Language file not found for namespace '{}' and language '{}'", namespace, language);
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Error loading language file for namespace '{}' and language '{}'", namespace, language, e);
        }

        return translations;
    }

    /**
     * Gets all loaded mod information.
     */
    public static Map<String, IModInfo> getAllMods() {
        Map<String, IModInfo> mods = new HashMap<>();
        ModList.get().getMods().forEach(modInfo -> {
            mods.put(modInfo.getModId(), modInfo);
        });
        return mods;
    }
}
