package com.octopus.teamcity.opentelemetry.server.helpers;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import com.octopus.teamcity.opentelemetry.server.LogMasker;
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

public class OTELHelperImpl implements OTELHelper {

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final ConcurrentHashMap<String, Span> spanMap;
    private final SpanProcessor spanProcessor;

    public OTELHelperImpl(Map<String, String> headers, String exporterEndpoint) {
        this.spanProcessor = buildSpanProcessor(headers, exporterEndpoint);

        Resource serviceNameResource = Resource
                .create(Attributes.of(ResourceAttributes.SERVICE_NAME, PluginConstants.SERVICE_NAME));
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .addSpanProcessor(spanProcessor)
                .build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        Loggers.SERVER.info("OTEL_PLUGIN: OpenTelemetry plugin started.");
        this.tracer = this.openTelemetry.getTracer(PluginConstants.TRACER_INSTRUMENTATION_NAME);
        this.spanMap = new ConcurrentHashMap<>();
    }

    private SpanProcessor buildSpanProcessor(Map<String, String> headers, String exporterEndpoint) {
        OtlpGrpcSpanExporterBuilder spanExporterBuilder = OtlpGrpcSpanExporter.builder();
        headers.forEach(spanExporterBuilder::addHeader);
        spanExporterBuilder.setEndpoint(exporterEndpoint);
        SpanExporter spanExporter = spanExporterBuilder.build();

        Loggers.SERVER.debug("OTEL_PLUGIN: Opentelemetry export headers: " + LogMasker.mask(headers.toString()));
        Loggers.SERVER.debug("OTEL_PLUGIN: Opentelemetry export endpoint: " + exporterEndpoint);

        return BatchSpanProcessor.builder(spanExporter).build();
    }

    @Override
    public boolean isReady() {
        return this.openTelemetry != null && this.tracer != null && this.spanMap != null;
    }

    @Override
    public Span getParentSpan(String buildId) {
        return this.spanMap.computeIfAbsent(buildId, key -> this.tracer.spanBuilder(buildId).startSpan());
    }

    @Override
    public Span createSpan(String spanName, Span parentSpan) {
        Loggers.SERVER.info("OTEL_PLUGIN: Creating child span " + spanName + " under parent " + parentSpan);
        return this.spanMap.computeIfAbsent(spanName, key -> this.tracer.spanBuilder(spanName).setParent(Context.current().with(parentSpan)).startSpan());
    }

    @Override
    public Span createTransientSpan(String spanName, Span parentSpan, long startTime) {
        return this.tracer.spanBuilder(spanName).setParent(Context.current().with(parentSpan)).setStartTimestamp(startTime, TimeUnit.MILLISECONDS).startSpan();
    }

    @Override
    public void removeSpan(String buildId) {
        this.spanMap.remove(buildId);
    }

    @Override
    public Span getSpan(String buildId) {
        return this.spanMap.get(buildId);
    }

    @Override
    public void addAttributeToSpan(Span span, String attributeName, Object attributeValue) {
        Loggers.SERVER.debug("OTEL_PLUGIN: Adding attribute to span " + attributeName + "=" + attributeValue);
        span.setAttribute(attributeName, attributeValue.toString());
    }

    @Override
    public void release() {
        spanProcessor.forceFlush();
        spanProcessor.close();
    }
}
