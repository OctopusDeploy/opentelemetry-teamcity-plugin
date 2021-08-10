package com.octopus.teamcity.opentelemetry.server;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LogMaskerTest {

    @Test
    void testMaskLowerCase() {
        int APIKEY_LENGTH = 31;
        String generatedString = RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH).toLowerCase(Locale.ROOT);
        assertEquals(LogMasker.API_KEY_REPLACEMENT_REGEX, LogMasker.mask(generatedString));
    }

    @Test
    void testMaskUpperCase() {
        int APIKEY_LENGTH = 31;
        String generatedString = RandomStringUtils.randomAlphanumeric(APIKEY_LENGTH).toUpperCase(Locale.ROOT);
        assertNotEquals(LogMasker.API_KEY_REPLACEMENT_REGEX, LogMasker.mask(generatedString));
    }

    @Test
    void testMaskLengthShort() {
        Random random = new Random();
        int API_KEY_LENGTH = random.nextInt(30);
        String generatedString = RandomStringUtils.randomAlphanumeric(API_KEY_LENGTH).toUpperCase(Locale.ROOT);
        assertNotEquals(LogMasker.API_KEY_REPLACEMENT_REGEX, LogMasker.mask(generatedString));
    }

    @Test
    void testMaskLengthLong() {
        Random random = new Random();
        int API_KEY_LENGTH = random.nextInt(100) + 31;
        String generatedString = RandomStringUtils.randomAlphanumeric(API_KEY_LENGTH).toUpperCase(Locale.ROOT);
        assertNotEquals(LogMasker.API_KEY_REPLACEMENT_REGEX, LogMasker.mask(generatedString));
    }
}