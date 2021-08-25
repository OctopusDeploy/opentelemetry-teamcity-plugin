package com.octopus.teamcity.opentelemetry.server;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LogMaskerTest {

    @Test
    void LowerCaseApiKeyGetsMasked() {
        int APIKEY_LENGTH = 31;
        String generatedString = RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH).toLowerCase(Locale.ROOT);
        assertFalse(LogMasker.mask(generatedString).contains(generatedString));
        assertTrue(LogMasker.mask(generatedString).contains(LogMasker.API_KEY_REPLACEMENT_REGEX));
    }

    @ParameterizedTest
    @ValueSource(ints = { 31, 32 })
    void ApiKeyMaskedForLowerCaseStringLengthGreaterThanOrEq31(int apiKeyLength) {
        String generatedString = RandomStringUtils.randomAlphanumeric(apiKeyLength).toLowerCase(Locale.ROOT);
        assertFalse(LogMasker.mask(generatedString).contains(generatedString));
        assertTrue(LogMasker.mask(generatedString).contains(LogMasker.API_KEY_REPLACEMENT_REGEX));
    }

    @ParameterizedTest
    @ValueSource(ints = { 29, 30 })
    void ApiKeyNotMaskedForLowerCaseStringLengthLessThan31(int apiKeyLength) {
        String generatedString = RandomStringUtils.randomAlphanumeric(apiKeyLength).toLowerCase(Locale.ROOT);
        assertTrue(LogMasker.mask(generatedString).contains(generatedString));
        assertFalse(LogMasker.mask(generatedString).contains(LogMasker.API_KEY_REPLACEMENT_REGEX));
    }

    @ParameterizedTest
    @ValueSource(ints = { 30, 31, 32 })
    void ApiKeyNotMaskedForUpperCaseString(int apiKeyLength) {
        String generatedString = RandomStringUtils.randomAlphanumeric(apiKeyLength).toUpperCase(Locale.ROOT);
        assertTrue(LogMasker.mask(generatedString).contains(generatedString));
        assertNotEquals(LogMasker.API_KEY_REPLACEMENT_REGEX, LogMasker.mask(generatedString));
    }

    @ParameterizedTest
    @ValueSource(chars = { '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '\n', '-', '=', '+', '_', '{', '}', '~', '<', '>', '?', '/', ':', ';', '[', ']', '|' })
    void ApiKeyNotMaskedForStringWithSpecialChars(char specialChar) {
        int APIKEY_LENGTH_FIRST_SPLIT = 15;
        int APIKEY_LENGTH_SECOND_SPLIT = 16;
        String generatedString = RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH_FIRST_SPLIT).toLowerCase(Locale.ROOT) + specialChar + RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH_SECOND_SPLIT).toLowerCase(Locale.ROOT);
        assertTrue(LogMasker.mask(generatedString).contains(generatedString));
        assertNotEquals(LogMasker.API_KEY_REPLACEMENT_REGEX, LogMasker.mask(generatedString));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2})
    void ApiKeyMaskedWhenContainedInStringBlock(int blockSetLength) {
        int APIKEY_LENGTH = 31;
        String generatedApiKey = RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH).toLowerCase(Locale.ROOT);
        String newLine = System.getProperty("line.separator");
        String textBlock = String.join(newLine,
                RandomStringUtils.randomAlphanumeric(blockSetLength).toLowerCase(Locale.ROOT),
                generatedApiKey,
                RandomStringUtils.randomAlphanumeric(blockSetLength).toLowerCase(Locale.ROOT),
                RandomStringUtils.randomAlphanumeric(blockSetLength).toLowerCase(Locale.ROOT));
        assertFalse(LogMasker.mask(textBlock).contains(generatedApiKey));
        assertTrue(LogMasker.mask(textBlock).contains(LogMasker.API_KEY_REPLACEMENT_REGEX));
    }

    @Test
    void ApiKeyNotMaskedWhenSplitInStringBlock() {
        int APIKEY_LENGTH = 31;
        String generatedApiKey = RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH).toLowerCase(Locale.ROOT);
        String newLine = System.getProperty("line.separator");
        String textBlock = String.join(newLine,
                generatedApiKey.substring(10),
                generatedApiKey.substring(11, 20),
                generatedApiKey.substring(21, 30),
                generatedApiKey.substring(31));
        assertFalse(LogMasker.mask(textBlock).contains(generatedApiKey));
        assertFalse(LogMasker.mask(textBlock).contains(LogMasker.API_KEY_REPLACEMENT_REGEX));
    }
}