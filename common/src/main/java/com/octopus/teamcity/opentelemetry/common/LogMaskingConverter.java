package com.octopus.teamcity.opentelemetry.common;

/*
  Created this LogMakingConverter Plugin for masking log statements
  Here masking for opentelemetry endpoint apikey.
 */
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name="LogMaskingConverter", category = "Converter")
@ConverterKeys({"spi"})
public class LogMaskingConverter extends LogEventPatternConverter {

    private static final Logger LOG = LogManager.getLogger();

    private static final String API_KEY_REGEX = "([a-z0-9]{31})";
    private static final Pattern API_KEY_PATTERN = Pattern.compile(API_KEY_REGEX);
    private static final String API_KEY_REPLACEMENT_REGEX = "XXXXXXXXXXXXXXXX";

    /**
     * Constructs an instance of LoggingEventPatternConverter.
     *
     * @param name  name of converter.
     * @param style CSS style for output.
     */
    protected LogMaskingConverter(String name, String style) {
        super(name, style);
    }

    public static LogMaskingConverter newInstance(String[] options) {
        return new LogMaskingConverter("spi",Thread.currentThread().getName());
    }


    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        String message = event.getMessage().getFormattedMessage();
        String maskedMessage;
        try {
            maskedMessage = mask(message);
        } catch (Exception e) {
            LOG.error("Failed to mask log message: {}", e.getMessage());
            maskedMessage = message;
        }
        toAppendTo.append(maskedMessage);
    }

    private String mask(String message) {
        Matcher matcher;
        StringBuffer buffer = new StringBuffer();

        matcher = API_KEY_PATTERN.matcher(message);
        return maskMatcher(matcher, buffer).toString();

    }

    private StringBuffer maskMatcher(Matcher matcher, StringBuffer buffer)
    {
        while (matcher.find()) {
            matcher.appendReplacement(buffer, LogMaskingConverter.API_KEY_REPLACEMENT_REGEX);
        }
        matcher.appendTail(buffer);
        return buffer;
    }
}
