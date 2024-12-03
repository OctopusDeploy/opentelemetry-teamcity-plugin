package com.octopus.teamcity.opentelemetry.server.endpoints.custom;

import com.octopus.teamcity.opentelemetry.server.*;
import com.octopus.teamcity.opentelemetry.server.endpoints.IOTELEndpointHandler;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class CustomOTELEndpointHandler implements IOTELEndpointHandler {
    private final PluginDescriptor pluginDescriptor;
    static Logger LOG = Logger.getLogger(CustomOTELEndpointHandler.class.getName());

    public CustomOTELEndpointHandler(
            PluginDescriptor pluginDescriptor) {
        this.pluginDescriptor = pluginDescriptor;
    }

    public ModelAndView getBuildOverviewModelAndView(SBuild build, Map<String, String> params, String traceId) {
        return new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewEmpty.jsp"));
    }

    @Override
    public Pair<SpanProcessor, SdkMeterProvider> buildSpanProcessorAndMeterProvider(BuildPromotion buildPromotion, String endpoint, Map<String, String> params) {
        Map<String, String> headers = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                var name = k.substring(PROPERTY_KEY_HEADERS.length());
                name = name.substring(1, name.length() - 1);
                var value = EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v;
                headers.put(name, value);
            }
        });
        return Pair.of(buildGrpcSpanProcessor(headers, endpoint), null);
    }

    @Override
    public SetProjectConfigurationSettingsRequest getSetProjectConfigurationSettingsRequest(HttpServletRequest request) {
        return new SetCustomProjectConfigurationSettingsRequest(request);
    }

    @Override
    public void mapParamsToModel(Map<String, String> params, Map<String, Object> model) {
        model.put("otelEndpoint", params.get(PROPERTY_KEY_ENDPOINT));

        var headers = new ArrayList<HeaderDto>();
        params.forEach((k,v)->{
            if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                var key = k.substring(PROPERTY_KEY_HEADERS.length());
                key = key.substring(1, key.length() - 1);
                var header = new HeaderDto(key,
                        EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v,
                        EncryptUtil.isScrambled(v) ? "password" : "plaintext");
                headers.add(header);
            }
        });

        model.put("otelHeaders", headers);
    }

    private SpanProcessor buildGrpcSpanProcessor(Map<String, String> headers, String exporterEndpoint) {

        OtlpGrpcSpanExporterBuilder spanExporterBuilder = OtlpGrpcSpanExporter.builder();
        headers.forEach(spanExporterBuilder::addHeader);
        spanExporterBuilder.setEndpoint(exporterEndpoint);
        SpanExporter spanExporter = spanExporterBuilder.build();

        return BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(BATCH_SPAN_PROCESSOR_MAX_QUEUE_SIZE)
                .setScheduleDelay(BATCH_SPAN_PROCESSOR_MAX_SCHEDULE_DELAY)
                .setMaxExportBatchSize(BATCH_SPAN_PROCESSOR_MAX_EXPORT_BATCH_SIZE)
                .build();
    }
}
