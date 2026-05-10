package com.pocky.autotranslator.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Tracks and displays translation progress in chat.
 */
public class TranslationProgress {
    private static volatile boolean translating = false;
    private static volatile boolean cancelled = false;
    private static volatile int totalItems = 0;
    private static volatile int completedItems = 0;
    private static volatile String currentPhase = "";
    private static volatile long lastChatUpdate = 0;
    private static final long CHAT_UPDATE_INTERVAL = 5000; // Update chat every 5 seconds

    /**
     * Starts tracking translation progress.
     */
    public static void startTranslation(int total) {
        translating = true;
        cancelled = false;
        totalItems = total;
        completedItems = 0;
        currentPhase = "Starting...";
        lastChatUpdate = 0;

        sendChatMessage("§a[AutoTranslator] Translation started: " + total + " items to translate");
    }

    /**
     * Updates the current phase.
     */
    public static void setPhase(String phase) {
        currentPhase = phase;
        updateChatIfNeeded();
    }

    /**
     * Updates progress.
     */
    public static void updateProgress(int completed) {
        completedItems = completed;
        updateChatIfNeeded();
    }

    /**
     * Marks translation as complete.
     */
    public static void completeTranslation() {
        translating = false;
        if (!cancelled) {
            sendChatMessage("§a[AutoTranslator] §fTranslation complete! §a" + completedItems + "/" + totalItems + " items translated");
        }
        reset();
    }

    /**
     * Cancels the current translation.
     */
    public static void cancel() {
        if (translating) {
            cancelled = true;
            translating = false;
            sendChatMessage("§c[AutoTranslator] Translation cancelled by user");
        }
    }

    /**
     * Checks if translation was cancelled.
     */
    public static boolean isCancelled() {
        return cancelled;
    }

    /**
     * Resets the tracker.
     */
    public static void reset() {
        translating = false;
        cancelled = false;
        totalItems = 0;
        completedItems = 0;
        currentPhase = "";
    }

    /**
     * Checks if translation is in progress.
     */
    public static boolean isTranslating() {
        return translating;
    }

    /**
     * Gets current progress percentage.
     */
    public static float getProgress() {
        if (totalItems == 0) return 0.0f;
        return Math.min(1.0f, (float) completedItems / totalItems);
    }

    /**
     * Updates chat with progress if enough time has passed.
     */
    private static void updateChatIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChatUpdate >= CHAT_UPDATE_INTERVAL && translating) {
            lastChatUpdate = currentTime;
            int percentage = (int)(getProgress() * 100);
            sendChatMessage("§a[AutoTranslator] §f" + currentPhase + " §7(" + completedItems + "/" + totalItems + " - " + percentage + "%)");
        }
    }

    /**
     * Sends a message to the player's chat.
     */
    public static void sendChatMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }

    /**
     * Gets current phase.
     */
    public static String getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Gets completed items.
     */
    public static int getCompletedItems() {
        return completedItems;
    }

    /**
     * Gets total items.
     */
    public static int getTotalItems() {
        return totalItems;
    }
}
