package com.pocky.autotranslator.resourcepack;

import com.pocky.autotranslator.AutoTranslatorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles automatic activation of the AutoTranslator resource pack.
 */
public class ResourcePackActivator {
    private static final String PACK_NAME = "file/AutoTranslator";

    /**
     * Activates the AutoTranslator resource pack if it exists and is not already active.
     */
    public static void activateResourcePack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            AutoTranslatorMod.LOGGER.warn("Minecraft instance not available, cannot activate resource pack");
            return;
        }

        PackRepository packRepository = minecraft.getResourcePackRepository();

        // Reload to detect new packs
        packRepository.reload();

        // Find the AutoTranslator pack
        Pack autoTranslatorPack = packRepository.getPack(PACK_NAME);

        if (autoTranslatorPack == null) {
            AutoTranslatorMod.LOGGER.warn("AutoTranslator resource pack not found in pack repository");
            return;
        }

        // Get currently selected packs
        List<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());

        // Check if already activated
        if (selectedPacks.contains(PACK_NAME)) {
            AutoTranslatorMod.LOGGER.info("AutoTranslator resource pack is already active");
            return;
        }

        // Add AutoTranslator pack to selected packs
        // Place it at high priority (near the beginning) so it overrides other packs
        selectedPacks.add(0, PACK_NAME);

        // Apply the new pack selection
        packRepository.setSelected(selectedPacks);

        AutoTranslatorMod.LOGGER.info("Activated AutoTranslator resource pack");

        // Reload resources to apply changes
        minecraft.reloadResourcePacks();
    }

    /**
     * Deactivates the AutoTranslator resource pack.
     */
    public static void deactivateResourcePack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        PackRepository packRepository = minecraft.getResourcePackRepository();
        List<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());

        if (selectedPacks.remove(PACK_NAME)) {
            packRepository.setSelected(selectedPacks);
            minecraft.reloadResourcePacks();
            AutoTranslatorMod.LOGGER.info("Deactivated AutoTranslator resource pack");
        }
    }

    /**
     * Checks if the AutoTranslator resource pack is currently active.
     */
    public static boolean isResourcePackActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }

        PackRepository packRepository = minecraft.getResourcePackRepository();
        return packRepository.getSelectedIds().contains(PACK_NAME);
    }

    /**
     * Enables the AutoTranslator resource pack (alias for activateResourcePack).
     */
    public static void enableResourcePack() {
        activateResourcePack();
    }

    /**
     * Disables the AutoTranslator resource pack (alias for deactivateResourcePack).
     */
    public static void disableResourcePack() {
        deactivateResourcePack();
    }

    /**
     * Checks if the AutoTranslator resource pack is enabled (alias for isResourcePackActive).
     */
    public static boolean isResourcePackEnabled() {
        return isResourcePackActive();
    }
}
