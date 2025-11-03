package com.pocky.autotranslator.translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pocky.autotranslator.AutoTranslatorMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Translation service with caching, multi-threading, and batch translation support.
 */
public class TranslationService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int BATCH_SIZE = 100; // Number of translations per batch
    private static final int THREAD_POOL_SIZE = 32; // Number of parallel threads

    private final GoogleTranslateClient client;
    private final Map<String, String> translationCache;
    private final Path cacheFile;
    private final int delayMs;

    public TranslationService(String targetLanguage, Path cacheDir, int delayMs) throws IOException {
        // Initialize Google Translate client (free, fast, no API key required!)
        this.client = new GoogleTranslateClient("en", targetLanguage);
        this.delayMs = delayMs;

        // Initialize cache
        Files.createDirectories(cacheDir);
        this.cacheFile = cacheDir.resolve("translation_cache_" + targetLanguage + ".json");
        this.translationCache = loadCache();

        AutoTranslatorMod.LOGGER.info("TranslationService initialized with {} cached translations",
                translationCache.size());
    }

    /**
     * Translates a single key-value pair.
     *
     * @param key   Translation key
     * @param value Original text
     * @return Translated text
     */
    public String translate(String key, String value) {
        // Check cache first
        if (translationCache.containsKey(key)) {
            return translationCache.get(key);
        }

        try {
            String translated = client.translate(value);
            translationCache.put(key, translated);
            return translated;
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to translate '{}': {}", key, e.getMessage());
            return value; // Return original on error
        }
    }

    /**
     * Translates a batch of key-value pairs with multi-threading.
     *
     * @param translations Map of translation key -> original text
     * @return Map of translation key -> translated text
     */
    public Map<String, String> translateBatch(Map<String, String> translations) {
        Map<String, String> results = new ConcurrentHashMap<>();
        Map<String, String> toTranslate = new HashMap<>();

        // Separate cached and non-cached translations
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            String key = entry.getKey();
            if (translationCache.containsKey(key)) {
                results.put(key, translationCache.get(key));
            } else {
                toTranslate.put(key, entry.getValue());
            }
        }

        AutoTranslatorMod.LOGGER.info("Using {} cached translations, translating {} new entries",
                results.size(), toTranslate.size());

        if (toTranslate.isEmpty()) {
            return results;
        }

        // Split into batches
        List<Map.Entry<String, String>> entries = new ArrayList<>(toTranslate.entrySet());
        List<List<Map.Entry<String, String>>> batches = new ArrayList<>();

        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            batches.add(entries.subList(i, Math.min(i + BATCH_SIZE, entries.size())));
        }

        AutoTranslatorMod.LOGGER.info("Processing {} entries in {} batches using {} threads",
                entries.size(), batches.size(), THREAD_POOL_SIZE);

        // Process batches in parallel
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<Map<String, String>>> futures = new ArrayList<>();

        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            final List<Map.Entry<String, String>> batch = batches.get(i);

            futures.add(executor.submit(() -> {
                return processBatch(batch, batchIndex + 1, batches.size());
            }));
        }

        // Collect results
        for (Future<Map<String, String>> future : futures) {
            try {
                Map<String, String> batchResults = future.get();
                results.putAll(batchResults);
                translationCache.putAll(batchResults);
            } catch (Exception e) {
                AutoTranslatorMod.LOGGER.error("Batch translation failed", e);
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            AutoTranslatorMod.LOGGER.error("Translation service interrupted", e);
            Thread.currentThread().interrupt();
        }

        return results;
    }

    /**
     * Processes a single batch of translations.
     */
    private Map<String, String> processBatch(List<Map.Entry<String, String>> batch, int batchNum, int totalBatches) {
        Map<String, String> results = new HashMap<>();

        try {
            // Prepare texts for batch translation
            List<String> textsToTranslate = new ArrayList<>();
            for (Map.Entry<String, String> entry : batch) {
                textsToTranslate.add(entry.getValue());
            }

            // Translate batch
            List<String> translatedTexts = client.translateBatch(textsToTranslate);

            // Map results back to keys
            for (int i = 0; i < batch.size(); i++) {
                String key = batch.get(i).getKey();
                String translated = i < translatedTexts.size() ? translatedTexts.get(i) : batch.get(i).getValue();
                results.put(key, translated);
            }

            // Only log every 10th batch to reduce overhead
            if (batchNum % 10 == 0 || batchNum == totalBatches) {
                AutoTranslatorMod.LOGGER.info("Progress: {}/{} batches completed", batchNum, totalBatches);
            }

            // Delay between batches to avoid rate limiting
            if (delayMs > 0 && batchNum < totalBatches) {
                Thread.sleep(delayMs);
            }

        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to process batch {}/{}", batchNum, totalBatches, e);
            // Return original texts on error
            for (Map.Entry<String, String> entry : batch) {
                results.put(entry.getKey(), entry.getValue());
            }
        }

        return results;
    }

    /**
     * Loads translation cache from disk.
     */
    private Map<String, String> loadCache() {
        if (!Files.exists(cacheFile)) {
            AutoTranslatorMod.LOGGER.info("No cache file found, starting with empty cache");
            return new ConcurrentHashMap<>();
        }

        try {
            String json = Files.readString(cacheFile);
            Map<String, String> cache = GSON.fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
            AutoTranslatorMod.LOGGER.info("Loaded {} translations from cache", cache.size());
            return new ConcurrentHashMap<>(cache);
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to load cache file", e);
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * Saves translation cache to disk.
     */
    public void saveCache() {
        try {
            String json = GSON.toJson(translationCache);
            Files.writeString(cacheFile, json);
            AutoTranslatorMod.LOGGER.info("Saved {} translations to cache", translationCache.size());
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to save cache file", e);
        }
    }

    /**
     * Clears the translation cache.
     */
    public void clearCache() {
        translationCache.clear();
        try {
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
                AutoTranslatorMod.LOGGER.info("Translation cache cleared");
            }
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to delete cache file", e);
        }
    }

    /**
     * Gets the number of cached translations.
     */
    public int getCacheSize() {
        return translationCache.size();
    }

    /**
     * Checks if the translation service is available.
     */
    public boolean isAvailable() {
        return client.isAvailable();
    }
}
