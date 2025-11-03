# Auto-Translator Mod

Automatically translates all items, text, and quests from English mods to any language using AI translators (DeepL or Google Translate).

## Description

Auto-Translator scans all loaded mods and FTB quests during Minecraft startup or when changing language and automatically detects missing translations. The mod supports two AI translators:

## Control Commands

The mod provides commands for full control over translations:

### Basic Commands
- `/autotranslator status` - Show current status (whether mods are being translated, quests enabled/disabled)
- `/autotranslator reload` - Restart translation process
- `/autotranslator cancel` - Cancel current translation process
- `/autotranslator help` - Show help for all commands

### Translation Management
- `/autotranslator enable` - Enable all translations (mods + quests)
- `/autotranslator disable` - Disable all translations (mods + quests)

### Separate Control
- `/autotranslator enable mods` - Enable only mod translations
- `/autotranslator disable mods` - Disable only mod translations
- `/autotranslator enable quests` - Check quest translations status
- `/autotranslator disable quests` - Disable only quest translations

**Note**: After enabling/disabling mod translations, press F3+T to apply changes.

## Configuration

The configuration file is created automatically in `config/autotranslator-client.toml`:

```toml
[AutoTranslator Configuration]
    # Enable automatic translation
    enabled = true
    # Automatically activate the generated resource pack
    autoActivateResourcePack = true
    # Delay between translation batch requests (in milliseconds)
    translationDelayMs = 0

[Translation Engine]
    # Choose translation engine: GOOGLE_TRANSLATE or DEEPL
    engine = "GOOGLE_TRANSLATE"
    # DeepL API Key (get free at https://www.deepl.com/pro-api)
    # Only needed if engine is set to DEEPL
    deeplApiKey = ""
```

**Important**: Translation language is no longer configured in settings! The mod automatically uses the language from Minecraft settings. Just change the language in game settings, and the mod will translate everything to the selected language.

## How It Works

1. **Language Detection**: The mod automatically gets the language from Minecraft settings (no configuration required)
2. **Mod Scanning**: Scans all language files of all mods
3. **FTB Quests Scanning**: Checks quest files in `config/ftbquests/quests/lang/`
4. **Missing Translation Detection**: Compares `en_us.json` and target language
5. **Translation**: Uses DeepL or Google Translate to translate missing strings
6. **Progress Display**: Shows progress in chat every 5 seconds
7. **Resource Pack Generation**: Creates a resource pack with all mod translations
8. **Quest Saving**: Creates a file with quest translations
9. **Activation**: Automatically activates the resource pack

**Instant Translation**: When changing language in Minecraft settings, the mod automatically starts without restarting the game!

## License

MIT License
