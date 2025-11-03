package com.pocky.autotranslator;

import com.pocky.autotranslator.chat.TranslationProgress;
import com.pocky.autotranslator.config.ModConfig;
import com.pocky.autotranslator.resourcepack.ResourcePackActivator;
import com.pocky.autotranslator.resourcepack.ResourcePackGenerator;
import com.pocky.autotranslator.scanner.FTBQuestsScanner;
import com.pocky.autotranslator.scanner.LanguageScanner;
import com.pocky.autotranslator.translation.TranslationService;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Main manager for the auto-translation process.
 * Coordinates scanning, translation, and resource pack generation.
 */
public class AutoTranslationManager {
    private static AutoTranslationManager instance;
    private static boolean translationInProgress = false;

    private final Path gameDirectory;
    private final TranslationService translationService;
    private final ResourcePackGenerator resourcePackGenerator;

    private AutoTranslationManager(Path gameDirectory) {
        this.gameDirectory = gameDirectory;

        // Get target language from Minecraft settings
        String minecraftLanguage = getMinecraftLanguage();
        AutoTranslatorMod.LOGGER.info("Detected Minecraft language: {}", minecraftLanguage);

        // Convert Minecraft language format (e.g., "ru_ru") to short code (e.g., "ru")
        String targetLang = minecraftLanguage.split("_")[0];
        AutoTranslatorMod.LOGGER.info("Using target language for translation: {}", targetLang);

        int delayMs = ModConfig.TRANSLATION_DELAY_MS.get();
        Path cacheDir = gameDirectory.resolve("autotranslator");

        try {
            this.translationService = new TranslationService(targetLang, cacheDir, delayMs);
            AutoTranslatorMod.LOGGER.info("Translation service initialized successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize translation service", e);
        }

        this.resourcePackGenerator = new ResourcePackGenerator(gameDirectory, minecraftLanguage);
    }

    /**
     * Gets the current language from Minecraft settings.
     *
     * @return Language code (e.g., "ru_ru", "uk_ua", "en_us")
     */
    private String getMinecraftLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            String languageCode = minecraft.options.languageCode;
            AutoTranslatorMod.LOGGER.info("Minecraft language code: {}", languageCode);
            return languageCode;
        }

        // Fallback to English if unable to get language
        AutoTranslatorMod.LOGGER.warn("Unable to get Minecraft language, falling back to en_us");
        return "en_us";
    }

    public static AutoTranslationManager getInstance(Path gameDirectory) {
        // Always recreate instance to pick up language changes
        instance = new AutoTranslationManager(gameDirectory);
        return instance;
    }

    public static AutoTranslationManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AutoTranslationManager not initialized");
        }
        return instance;
    }

    /**
     * Resets the singleton instance.
     * Called when language changes to force recreation with new language.
     */
    public static void resetInstance() {
        instance = null;
    }

    /**
     * Starts the auto-translation process.
     * This should be called after all mods and resources are loaded.
     */
    public void startTranslation() {
        if (!ModConfig.ENABLED.get()) {
            AutoTranslatorMod.LOGGER.info("Auto-translation is disabled in config");
            return;
        }

        // Don't translate if language is English (source language)
        String minecraftLanguage = getMinecraftLanguage();
        if (minecraftLanguage.startsWith("en_")) {
            AutoTranslatorMod.LOGGER.info("Language is English, skipping translation");
            return;
        }

        if (translationInProgress) {
            AutoTranslatorMod.LOGGER.warn("Translation already in progress, skipping");
            return;
        }

        translationInProgress = true;

        // Run in a separate thread to avoid blocking the game
        Thread translationThread = new Thread(() -> {
            try {
                performTranslation();
            } catch (Exception e) {
                AutoTranslatorMod.LOGGER.error("Error during translation process", e);
            } finally {
                translationInProgress = false;
            }
        }, "AutoTranslator-Worker");

        translationThread.setDaemon(true);
        translationThread.start();
    }

    /**
     * Performs the complete translation process.
     */
    private void performTranslation() {
        AutoTranslatorMod.LOGGER.info("Starting auto-translation process...");

        try {
            // Step 1: Scan for missing translations
            TranslationProgress.setPhase("Scanning mods...");
            AutoTranslatorMod.LOGGER.info("Scanning for missing translations...");
            Map<String, Map<String, String>> missingTranslations = scanMissingTranslations();

            if (missingTranslations.isEmpty()) {
                AutoTranslatorMod.LOGGER.info("No missing translations found!");
                TranslationProgress.sendChatMessage("§a[AutoTranslator] No missing translations found");
                TranslationProgress.reset();
                return;
            }

            int totalMissing = missingTranslations.values().stream()
                    .mapToInt(Map::size)
                    .sum();
            AutoTranslatorMod.LOGGER.info("Found {} missing translations across {} namespaces",
                    totalMissing, missingTranslations.size());

            // Initialize progress tracking
            TranslationProgress.startTranslation(totalMissing);

            // Step 2: Translate missing entries
            TranslationProgress.setPhase("Translating mods...");
            AutoTranslatorMod.LOGGER.info("Translating missing entries...");
            Map<String, Map<String, String>> translatedEntries = new HashMap<>();

            int completedCount = 0;
            for (Map.Entry<String, Map<String, String>> entry : missingTranslations.entrySet()) {
                // Check if cancelled
                if (TranslationProgress.isCancelled()) {
                    AutoTranslatorMod.LOGGER.info("Translation cancelled by user");
                    TranslationProgress.reset();
                    return;
                }

                String namespace = entry.getKey();
                Map<String, String> toTranslate = entry.getValue();

                TranslationProgress.setPhase("Translating: " + namespace);
                AutoTranslatorMod.LOGGER.info("Translating namespace: {} ({} entries)",
                        namespace, toTranslate.size());

                Map<String, String> translated = translationService.translateBatch(toTranslate);
                translatedEntries.put(namespace, translated);

                // Update progress
                completedCount += toTranslate.size();
                TranslationProgress.updateProgress(completedCount);
            }

            // Save translation cache
            translationService.saveCache();

            // Step 3: Generate resource pack
            TranslationProgress.setPhase("Generating resource pack...");
            AutoTranslatorMod.LOGGER.info("Generating resource pack...");
            resourcePackGenerator.generateResourcePack(translatedEntries);

            // Step 4: Translate FTB Quests
            TranslationProgress.setPhase("Scanning FTB Quests...");
            TranslationProgress.sendChatMessage("§a[AutoTranslator] §fScanning FTB Quests...");
            AutoTranslatorMod.LOGGER.info("Scanning FTB Quests...");
            String minecraftLanguage = getMinecraftLanguage();
            FTBQuestsScanner questsScanner = new FTBQuestsScanner("en_us", minecraftLanguage);
            Map<String, String> missingQuests = questsScanner.scanForMissingTranslations(gameDirectory);

            if (!missingQuests.isEmpty()) {
                TranslationProgress.setPhase("Translating FTB Quests...");
                TranslationProgress.sendChatMessage("§a[AutoTranslator] §fTranslating §e" + missingQuests.size() + " §fFTB Quests entries...");
                AutoTranslatorMod.LOGGER.info("Translating {} FTB Quests entries...", missingQuests.size());
                Map<String, String> translatedQuests = translationService.translateBatch(missingQuests);

                // Save translation cache
                translationService.saveCache();

                // Write translated quests
                questsScanner.writeTranslations(gameDirectory, translatedQuests);
                AutoTranslatorMod.LOGGER.info("FTB Quests translation complete!");
                TranslationProgress.sendChatMessage("§a[AutoTranslator] §fFTB Quests translation complete!");
            } else {
                AutoTranslatorMod.LOGGER.info("No missing FTB Quests translations found");
                TranslationProgress.sendChatMessage("§a[AutoTranslator] §fNo missing FTB Quests translations");
            }

            // Step 5: Activate resource pack
            if (ModConfig.AUTO_ACTIVATE_RESOURCEPACK.get()) {
                TranslationProgress.setPhase("Activating resource pack...");
                AutoTranslatorMod.LOGGER.info("Activating resource pack...");
                // Schedule activation on the main thread
                Minecraft.getInstance().execute(() -> {
                    ResourcePackActivator.activateResourcePack();
                });
            }

            AutoTranslatorMod.LOGGER.info("Auto-translation process complete!");
            TranslationProgress.completeTranslation();

        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to complete translation process", e);
            TranslationProgress.reset();
        }
    }

    /**
     * Scans all loaded mods for missing translations.
     */
    private Map<String, Map<String, String>> scanMissingTranslations() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            AutoTranslatorMod.LOGGER.error("Minecraft instance not available");
            return new HashMap<>();
        }

        String minecraftLanguage = getMinecraftLanguage();
        LanguageScanner scanner = new LanguageScanner("en_us", minecraftLanguage);

        // Get all pack resources
        Iterable<PackResources> packResources = minecraft.getResourceManager()
                .listPacks()
                .toList();

        return scanner.scanForMissingTranslations(packResources);
    }

    /**
     * Forces a re-translation of all content.
     */
    public void forceRetranslate() {
        translationService.clearCache();
        try {
            resourcePackGenerator.deleteResourcePack();
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to delete resource pack", e);
        }
        startTranslation();
    }

    public TranslationService getTranslationService() {
        return translationService;
    }

    public ResourcePackGenerator getResourcePackGenerator() {
        return resourcePackGenerator;
    }

    public boolean isTranslationInProgress() {
        return translationInProgress;
    }
}
