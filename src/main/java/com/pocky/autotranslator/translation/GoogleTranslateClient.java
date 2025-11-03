package com.pocky.autotranslator.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.pocky.autotranslator.AutoTranslatorMod;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for Google Translate (unofficial API).
 *
 * Features:
 * - Completely free with no API key required
 * - No character limits (reasonable use)
 * - Fast and reliable
 * - High quality translations
 *
 * Note: This uses Google Translate's public web interface,
 * not the official paid API.
 */
public class GoogleTranslateClient {
    private static final String API_URL = "https://translate.googleapis.com/translate_a/single";
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final String sourceLanguage;
    private final String targetLanguage;

    public GoogleTranslateClient(String sourceLanguage, String targetLanguage) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;

        AutoTranslatorMod.LOGGER.info("Initialized Google Translate client: {} -> {}", sourceLanguage, targetLanguage);
        AutoTranslatorMod.LOGGER.info("Using free Google Translate API (no limits, no API key required)");
    }

    /**
     * Translates a single text string.
     *
     * @param text Text to translate
     * @return Translated text
     */
    public String translate(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // Build URL with query parameters
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);

        String url = String.format(
            "%s?client=gtx&sl=%s&tl=%s&dt=t&q=%s",
            API_URL,
            sourceLanguage,
            targetLanguage,
            encodedText
        );

        // Send HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Google Translate API error: HTTP " + response.statusCode());
        }

        // Parse response JSON
        // Format: [[["translated text","original text",null,null,3]],null,"en"]
        try {
            JsonArray rootArray = JsonParser.parseString(response.body()).getAsJsonArray();
            JsonArray translationsArray = rootArray.get(0).getAsJsonArray();

            StringBuilder translatedText = new StringBuilder();
            for (int i = 0; i < translationsArray.size(); i++) {
                JsonArray translationPart = translationsArray.get(i).getAsJsonArray();
                if (translationPart.size() > 0 && !translationPart.get(0).isJsonNull()) {
                    translatedText.append(translationPart.get(0).getAsString());
                }
            }

            return translatedText.toString();
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Failed to parse Google Translate response: {}", response.body(), e);
            throw new Exception("Failed to parse translation response: " + e.getMessage());
        }
    }

    /**
     * Translates multiple texts.
     *
     * @param texts List of texts to translate
     * @return List of translated texts
     */
    public List<String> translateBatch(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> results = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            try {
                String translated = translate(texts.get(i));
                results.add(translated);
            } catch (Exception e) {
                // Return original text on error (silent fail to avoid log spam)
                results.add(texts.get(i));
            }
        }

        return results;
    }

    /**
     * Checks if the Google Translate service is available.
     *
     * @return true if service is reachable
     */
    public boolean isAvailable() {
        try {
            // Try to translate a simple test string
            String test = translate("test");
            return test != null && !test.isEmpty();
        } catch (Exception e) {
            AutoTranslatorMod.LOGGER.error("Google Translate service is not available", e);
            return false;
        }
    }
}
