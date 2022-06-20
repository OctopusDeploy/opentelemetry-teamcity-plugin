package com.octopus.teamcity.opentelemetry.server.helpers;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jetbrains.buildServer.log.Loggers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public interface OTELHelper {
    boolean isReady();

    Span getParentSpan(String buildId);

    Span createSpan(String spanName, Span parentSpan);

    Span createTransientSpan(String spanName, Span parentSpan, long startTime);

    void removeSpan(String buildId);

    Span getSpan(String buildId);

    void addAttributeToSpan(Span span, String attributeName, Object attributeValue);
}

