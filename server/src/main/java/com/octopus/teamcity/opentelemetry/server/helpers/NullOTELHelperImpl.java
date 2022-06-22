package com.octopus.teamcity.opentelemetry.server.helpers;

import io.opentelemetry.api.trace.Span;

public class NullOTELHelperImpl implements OTELHelper {
    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public Span getParentSpan(String buildId) {
        return null;
    }

    @Override
    public Span createSpan(String spanName, Span parentSpan) {
        return null;
    }

    @Override
    public Span createTransientSpan(String spanName, Span parentSpan, long startTime) {
        return null;
    }

    @Override
    public void removeSpan(String buildId) {
    }

    @Override
    public Span getSpan(String buildId) {
        return null;
    }

    @Override
    public void addAttributeToSpan(Span span, String attributeName, Object attributeValue) {
    }
}
