package com.octopus.teamcity.opentelemetry.server.endpoints.custom;

import com.octopus.teamcity.opentelemetry.server.*;
import com.octopus.teamcity.opentelemetry.server.endpoints.IOTELEndpointHandler;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.log4j.Logger;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.PROPERTY_KEY_HEADERS;

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
    public SpanProcessor BuildSpanProcessor(String endpoint, Map<String, String> params) {
        Map<String, String> headers = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                var name = k.substring(PROPERTY_KEY_HEADERS.length());
                name = name.substring(1, name.length() - 1);
                var value = EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v;
                headers.put(name, value);
            }
        });
        return buildGrpcSpanProcessor(headers, endpoint);
    }

    @Override
    public SetProjectConfigurationSettingsRequest GetSetProjectConfigurationSettingsRequest(HttpServletRequest request) {
        return new SetCustomProjectConfigurationSettingsRequest(request);
    }

    private SpanProcessor buildGrpcSpanProcessor(Map<String, String> headers, String exporterEndpoint) {

        OtlpGrpcSpanExporterBuilder spanExporterBuilder = OtlpGrpcSpanExporter.builder();
        headers.forEach(spanExporterBuilder::addHeader);
        spanExporterBuilder.setEndpoint(exporterEndpoint);
        SpanExporter spanExporter = spanExporterBuilder.build();

        LOG.debug("OTEL_PLUGIN: Opentelemetry export headers: " + LogMasker.mask(headers.toString()));
        LOG.debug("OTEL_PLUGIN: Opentelemetry export endpoint: " + exporterEndpoint);

        return BatchSpanProcessor.builder(spanExporter).build();
    }
}
