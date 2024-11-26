package com.octopus.teamcity.opentelemetry.server.endpoints.zipkin;

import com.octopus.teamcity.opentelemetry.server.SetProjectConfigurationSettingsRequest;
import com.octopus.teamcity.opentelemetry.server.endpoints.IOTELEndpointHandler;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.PROPERTY_KEY_ENDPOINT;

public class ZipKinOTELEndpointHandler implements IOTELEndpointHandler {
    private final PluginDescriptor pluginDescriptor;

    public ZipKinOTELEndpointHandler(
            PluginDescriptor pluginDescriptor) {
        this.pluginDescriptor = pluginDescriptor;
    }

    @NotNull
    public ModelAndView getBuildOverviewModelAndView(SBuild build, Map<String, String> params, String traceId) {
        final ModelAndView mv = new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewZipkinExtension.jsp"));

        var model = mv.getModel();
        model.put("traceId", traceId);
        model.put("endpoint", params.get(PROPERTY_KEY_ENDPOINT).replaceAll("/$", ""));
        return mv;
    }

    @Override
    public SpanProcessor buildSpanProcessor(String endpoint, Map<String, String> params) {
        return buildZipkinSpanProcessor(endpoint);
    }

    @Nullable
    @Override
    public MetricExporter buildMetricsExporter(String endpoint, Map<String, String> params) {
        return null;
    }

    private SpanProcessor buildZipkinSpanProcessor(String exporterEndpoint) {
        String endpoint = String.format("%s/api/v2/spans", exporterEndpoint);
        ZipkinSpanExporter zipkinExporter = ZipkinSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();

        return BatchSpanProcessor.builder(zipkinExporter).build();
    }

    @Override
    public SetProjectConfigurationSettingsRequest getSetProjectConfigurationSettingsRequest(HttpServletRequest request) {
        return new SetZipkinProjectConfigurationSettingsRequest(request);
    }

    @Override
    public void mapParamsToModel(Map<String, String> params, Map<String, Object> model) {
        model.put("otelEndpoint", params.get(PROPERTY_KEY_ENDPOINT));
    }
}
