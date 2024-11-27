package com.octopus.teamcity.opentelemetry.server.helpers;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class OTELMetrics {

    private static final AtomicBoolean metricsConfigured = new AtomicBoolean(false);
    private static SdkMeterProvider sdkMeterProvider;

    public static SdkMeterProvider getOTELMeterProvider(@Nullable MetricExporter metricExporter, Resource serviceNameResource) {
        if (metricsConfigured.get()) return sdkMeterProvider;
        metricsConfigured.set(true);

        var loggingMetricExporter = LoggingMetricExporter.create();
        var consoleLogMetricReader = PeriodicMetricReader.builder(loggingMetricExporter)
                .setInterval(Duration.ofSeconds(10))
                .build();
        var meterProviderBuilder = SdkMeterProvider.builder()
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .registerMetricReader(consoleLogMetricReader);
        if (metricExporter != null) {
            var providedMetricExporterBuilder = PeriodicMetricReader.builder(metricExporter)
                    .setInterval(Duration.ofSeconds(10))
                    .build();
            meterProviderBuilder
                    .registerMetricReader(providedMetricExporterBuilder);
        }
        sdkMeterProvider = meterProviderBuilder.build();
        var globalOpenTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .build();

        GlobalOpenTelemetry.set(globalOpenTelemetry);

        // Shutdown hooks to close resources properly
        Runtime.getRuntime().addShutdownHook(new Thread(sdkMeterProvider::close));
        return sdkMeterProvider;
    }
}
