package com.pocky.autotranslator.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.pocky.autotranslator.AutoTranslationManager;
import com.pocky.autotranslator.AutoTranslatorMod;
import com.pocky.autotranslator.chat.TranslationProgress;
import com.pocky.autotranslator.resourcepack.ResourcePackActivator;
import com.pocky.autotranslator.scanner.FTBQuestsScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Commands for controlling AutoTranslator mod.
 */
public class TranslationCommands {

    /**
     * Registers all translation commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("autotranslator")
                .then(Commands.literal("status")
                    .executes(TranslationCommands::status))
                .then(Commands.literal("cancel")
                    .executes(TranslationCommands::cancel))
                .then(Commands.literal("enable")
                    .executes(TranslationCommands::enable)
                    .then(Commands.literal("mods")
                        .executes(TranslationCommands::enableMods))
                    .then(Commands.literal("quests")
                        .executes(TranslationCommands::enableQuests)))
                .then(Commands.literal("disable")
                    .executes(TranslationCommands::disable)
                    .then(Commands.literal("mods")
                        .executes(TranslationCommands::disableMods))
                    .then(Commands.literal("quests")
                        .executes(TranslationCommands::disableQuests)))
                .then(Commands.literal("reload")
                    .executes(TranslationCommands::reload))
                .then(Commands.literal("help")
                    .executes(TranslationCommands::help))
                .executes(TranslationCommands::help)
        );
    }

    /**
     * Shows translation status.
     */
    private static int status(CommandContext<CommandSourceStack> context) {
        if (TranslationProgress.isTranslating()) {
            int percentage = (int)(TranslationProgress.getProgress() * 100);
            context.getSource().sendSuccess(() -> Component.literal(
                "§a[AutoTranslator] §fStatus: §eTranslating\n" +
                "§fPhase: §7" + TranslationProgress.getCurrentPhase() + "\n" +
                "§fProgress: §7" + TranslationProgress.getCompletedItems() + "/" +
                TranslationProgress.getTotalItems() + " (" + percentage + "%)"
            ), false);
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            final boolean modsEnabled = ResourcePackActivator.isResourcePackEnabled();

            // Check quests status
            final boolean questsEnabled;
            if (minecraft != null) {
                String minecraftLanguage = getMinecraftLanguage(minecraft);
                FTBQuestsScanner scanner = new FTBQuestsScanner("en_us", minecraftLanguage);
                questsEnabled = scanner.translationsExist(minecraft.gameDirectory.toPath());
            } else {
                questsEnabled = false;
            }

            context.getSource().sendSuccess(() -> Component.literal(
                "§a[AutoTranslator] §fStatus: §aIdle\n" +
                "§fMods translations: " + (modsEnabled ? "§aEnabled" : "§cDisabled") + "\n" +
                "§fQuests translations: " + (questsEnabled ? "§aEnabled" : "§cDisabled")
            ), false);
        }
        return 1;
    }

    /**
     * Cancels current translation.
     */
    private static int cancel(CommandContext<CommandSourceStack> context) {
        if (TranslationProgress.isTranslating()) {
            TranslationProgress.cancel();
            context.getSource().sendSuccess(() -> Component.literal(
                "§c[AutoTranslator] Translation cancelled"
            ), false);
        } else {
            context.getSource().sendFailure(Component.literal(
                "§c[AutoTranslator] No translation in progress"
            ));
        }
        return 1;
    }

    /**
     * Enables translation resource pack.
     */
    private static int enable(CommandContext<CommandSourceStack> context) {
        ResourcePackActivator.enableResourcePack();
        context.getSource().sendSuccess(() -> Component.literal(
            "§a[AutoTranslator] Translations enabled! Reload resources (F3+T) to apply."
        ), false);
        return 1;
    }

    /**
     * Disables all translations (mods and quests).
     */
    private static int disable(CommandContext<CommandSourceStack> context) {
        // Disable mods translations
        ResourcePackActivator.disableResourcePack();

        // Disable quests translations
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            String minecraftLanguage = getMinecraftLanguage(minecraft);
            FTBQuestsScanner scanner = new FTBQuestsScanner("en_us", minecraftLanguage);
            scanner.removeTranslations(minecraft.gameDirectory.toPath());
        }

        context.getSource().sendSuccess(() -> Component.literal(
            "§c[AutoTranslator] All translations disabled! Reload resources (F3+T) to apply."
        ), false);
        return 1;
    }

    /**
     * Reloads/restarts translation.
     */
    private static int reload(CommandContext<CommandSourceStack> context) {
        if (TranslationProgress.isTranslating()) {
            context.getSource().sendFailure(Component.literal(
                "§c[AutoTranslator] Translation already in progress. Use '/autotranslator cancel' first."
            ));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal(
            "§a[AutoTranslator] Starting translation..."
        ), false);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            AutoTranslationManager manager = AutoTranslationManager.getInstance(
                minecraft.gameDirectory.toPath()
            );
            manager.startTranslation();
        }

        return 1;
    }

    /**
     * Enables mods translations only.
     */
    private static int enableMods(CommandContext<CommandSourceStack> context) {
        ResourcePackActivator.enableResourcePack();
        context.getSource().sendSuccess(() -> Component.literal(
            "§a[AutoTranslator] Mods translations enabled! Reload resources (F3+T) to apply."
        ), false);
        return 1;
    }

    /**
     * Disables mods translations only.
     */
    private static int disableMods(CommandContext<CommandSourceStack> context) {
        ResourcePackActivator.disableResourcePack();
        context.getSource().sendSuccess(() -> Component.literal(
            "§c[AutoTranslator] Mods translations disabled! Reload resources (F3+T) to apply."
        ), false);
        return 1;
    }

    /**
     * Enables quests translations only (no-op, quests are always enabled if file exists).
     */
    private static int enableQuests(CommandContext<CommandSourceStack> context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            String minecraftLanguage = getMinecraftLanguage(minecraft);
            FTBQuestsScanner scanner = new FTBQuestsScanner("en_us", minecraftLanguage);
            if (scanner.translationsExist(minecraft.gameDirectory.toPath())) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§a[AutoTranslator] Quests translations are already enabled"
                ), false);
            } else {
                context.getSource().sendFailure(Component.literal(
                    "§c[AutoTranslator] No quests translations found. Use '/autotranslator reload' to create them."
                ));
            }
        }
        return 1;
    }

    /**
     * Disables quests translations only.
     */
    private static int disableQuests(CommandContext<CommandSourceStack> context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            String minecraftLanguage = getMinecraftLanguage(minecraft);
            FTBQuestsScanner scanner = new FTBQuestsScanner("en_us", minecraftLanguage);
            if (scanner.removeTranslations(minecraft.gameDirectory.toPath())) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§c[AutoTranslator] Quests translations disabled!"
                ), false);
            } else {
                context.getSource().sendFailure(Component.literal(
                    "§c[AutoTranslator] No quests translations found"
                ));
            }
        }
        return 1;
    }

    /**
     * Gets Minecraft language code.
     */
    private static String getMinecraftLanguage(Minecraft minecraft) {
        if (minecraft != null && minecraft.options != null) {
            return minecraft.options.languageCode;
        }
        return "en_us";
    }

    /**
     * Shows help message.
     */
    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
            "§a[AutoTranslator] §fAvailable commands:\n" +
            "§7/autotranslator status §f- Show translation status\n" +
            "§7/autotranslator cancel §f- Cancel current translation\n" +
            "§7/autotranslator enable §f- Enable all translations\n" +
            "§7/autotranslator enable mods §f- Enable mods translations\n" +
            "§7/autotranslator enable quests §f- Enable quests translations\n" +
            "§7/autotranslator disable §f- Disable all translations\n" +
            "§7/autotranslator disable mods §f- Disable mods translations\n" +
            "§7/autotranslator disable quests §f- Disable quests translations\n" +
            "§7/autotranslator reload §f- Restart translation\n" +
            "§7/autotranslator help §f- Show this help"
        ), false);
        return 1;
    }
}
