package com.octopus.teamcity.opentelemetry.server.helpers;

import io.opentelemetry.api.trace.Span;

import javax.annotation.Nullable;

public class NullOTELHelperImpl implements OTELHelper {
    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public Span getOrCreateParentSpan(String buildId) {
        return null;
    }

    @Override
    public Span createSpan(String spanName, Span parentSpan, String parentSpanName) {
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
    @Nullable
    public Span getSpan(String buildId) {
        return null;
    }

    @Override
    public void addAttributeToSpan(Span span, String attributeName, Object attributeValue) {
    }

    @Override
    public void release(String helperName) {
    }
}
