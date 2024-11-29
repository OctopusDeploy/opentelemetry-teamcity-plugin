package com.octopus.teamcity.opentelemetry.server.helpers;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import javax.annotation.Nullable;
import java.time.Duration;

public class OTELMetrics {

    @Nullable
    public static SdkMeterProvider getOTELMeterProvider(@Nullable MetricExporter metricExporter, Resource serviceNameResource) {
        if (metricExporter == null) return null;

        var providedMetricExporter = PeriodicMetricReader.builder(metricExporter)
                .setInterval(Duration.ofSeconds(10))
                .build();

        return SdkMeterProvider.builder()
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .registerMetricReader(providedMetricExporter)
                .build();
    }
}
