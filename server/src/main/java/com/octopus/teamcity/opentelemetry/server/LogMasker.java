package com.octopus.teamcity.opentelemetry.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogMasker {

    public static final String API_KEY_REPLACEMENT_REGEX = "XXXXXXXXXXXXXXXX";

    private LogMasker() {
        throw new IllegalStateException("Utility class LogMasker should not be instantiated ");
    }

    public static String mask(String message) {
        final String HONEYCOMB_API_KEY_REGEX = "([a-z0-9]{31})";
        final Pattern apikeyPattern = Pattern.compile(HONEYCOMB_API_KEY_REGEX);
        StringBuffer buffer = new StringBuffer();

        Matcher matcher = apikeyPattern.matcher(message);
        while (matcher.find()) {
            matcher.appendReplacement(buffer, API_KEY_REPLACEMENT_REGEX);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
