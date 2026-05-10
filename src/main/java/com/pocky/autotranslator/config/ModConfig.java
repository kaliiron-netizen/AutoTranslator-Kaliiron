package com.pocky.autotranslator.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue AUTO_ACTIVATE_RESOURCEPACK;
    public static final ModConfigSpec.IntValue TRANSLATION_DELAY_MS;

    static {
        BUILDER.push("AutoTranslator Configuration");

        BUILDER.comment("Note: Target language is automatically detected from Minecraft language settings!",
                        "Change your Minecraft language in Options > Language to translate to a different language.");

        ENABLED = BUILDER
                .comment("Enable automatic translation")
                .define("enabled", true);

        AUTO_ACTIVATE_RESOURCEPACK = BUILDER
                .comment("Automatically activate the generated resource pack")
                .define("autoActivateResourcePack", true);

        TRANSLATION_DELAY_MS = BUILDER
                .comment("Delay between translation batch requests (in milliseconds)",
                        "Default: 0ms. Google Translate is fast and has no rate limits for reasonable use.",
                        "Increase this value if you experience issues.",
                        "Note: Uses Google Translate API (free, unlimited, no API key required)")
                // Adjust this value to default at 2 seconds to reduce the possibility of API blocking
                .defineInRange("translationDelayMs", 2000, 0, 5000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
