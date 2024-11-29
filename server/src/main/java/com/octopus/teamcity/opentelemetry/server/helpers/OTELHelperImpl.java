package com.octopus.teamcity.opentelemetry.server.helpers;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OTELHelperImpl implements OTELHelper {
    static Logger LOG = Logger.getLogger(OTELHelperImpl.class.getName());
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final ConcurrentHashMap<String, Span> spanMap;
    private final SdkTracerProvider sdkTracerProvider;
    private final String helperName;
    @Nullable
    private final SdkMeterProvider meterProvider;

    public OTELHelperImpl(
            SpanProcessor spanProcessor,
            @Nullable
            SdkMeterProvider meterProvider,
            String helperName) {
        this.helperName = helperName;
        Resource serviceNameResource = Resource
                .create(Attributes.of(ResourceAttributes.SERVICE_NAME, PluginConstants.SERVICE_NAME));
        this.sdkTracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .addSpanProcessor(spanProcessor)
                .build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        this.tracer = this.openTelemetry.getTracer(PluginConstants.TRACER_INSTRUMENTATION_NAME);
        this.spanMap = new ConcurrentHashMap<>();
        this.meterProvider = meterProvider;
    }

    @Override
    public boolean isReady() {
        return this.openTelemetry != null && this.tracer != null && this.spanMap != null;
    }

    @Override
    public Span getOrCreateParentSpan(String buildId) {
        return this.spanMap.computeIfAbsent(buildId, key -> this.tracer.spanBuilder(buildId).startSpan());
    }

    @Override
    public Span createSpan(String spanName, Span parentSpan, String parentSpanName) {
        LOG.info("Creating child span " + spanName + " under parent " + parentSpanName);
        return this.spanMap.computeIfAbsent(spanName, key -> this.tracer
                .spanBuilder(spanName)
                .setParent(Context.current().with(parentSpan))
                .startSpan());
    }

    @Override
    public Span createTransientSpan(String spanName, Span parentSpan, long startTime) {
        return this.tracer.spanBuilder(spanName)
                .setParent(Context.current().with(parentSpan))
                .setStartTimestamp(startTime, TimeUnit.MILLISECONDS)
                .startSpan();
    }

    @Override
    public void removeSpan(String spanName) {
        this.spanMap.remove(spanName);
    }

    @Override
    @Nullable
    public Span getSpan(String spanName) {
        return this.spanMap.get(spanName);
    }

    @Override
    public void addAttributeToSpan(Span span, String attributeName, Object attributeValue) {
        span.setAttribute(attributeName, attributeValue.toString());
    }

    @Override
    public void release(String helperName) {
        LOG.info("Cleaning up OTELHelperImpl named '" + helperName + "'; there are still " + this.spanMap.size() + " spans in the map");

        this.sdkTracerProvider.forceFlush();
        this.sdkTracerProvider.close();
        if (this.meterProvider != null)
            this.meterProvider.close();
        this.spanMap.clear();
    }
}
