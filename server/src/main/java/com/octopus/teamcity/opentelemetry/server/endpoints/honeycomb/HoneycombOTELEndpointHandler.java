package com.octopus.teamcity.opentelemetry.server.endpoints.honeycomb;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import com.octopus.teamcity.opentelemetry.server.*;
import com.octopus.teamcity.opentelemetry.server.endpoints.IOTELEndpointHandler;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HoneycombOTELEndpointHandler implements IOTELEndpointHandler {

    private final PluginDescriptor pluginDescriptor;
    static Logger LOG = Logger.getLogger(HoneycombOTELEndpointHandler.class.getName());

    public HoneycombOTELEndpointHandler(PluginDescriptor pluginDescriptor) {
        this.pluginDescriptor = pluginDescriptor;
    }

    @NotNull
    public ModelAndView getBuildOverviewModelAndView(SBuild build, Map<String, String> params, String traceId) {
        final ModelAndView mv = new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewHoneycombExtension.jsp"));

        var model = mv.getModel();
        model.put("team", params.get(PROPERTY_KEY_HONEYCOMB_TEAM));
        model.put("dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
        model.put("traceId", traceId);

        //we pad the time to ensure that we get all the spans, just in case we get a slight diff in the
        //timestamps between the traces and the server start time.
        //this will also help us see the whole chain better
        long twoHoursInSeconds = 7200;
        Date startDate = build.getServerStartDate();
        model.put("buildStart", (startDate.getTime() / 1000L) - twoHoursInSeconds);
        var finishDate = build.getFinishDate();
        model.put("buildEnd", ((finishDate == null ? startDate : finishDate).getTime() / 1000L) + twoHoursInSeconds);
        return mv;
    }

    @Override
    public SpanProcessor buildSpanProcessor(String endpoint, Map<String, String> params) {
        Map<String, String> headers = new HashMap<>();
        //todo: add a setting to say "use classic" or "use environments"
        headers.put("x-honeycomb-dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
        headers.put("x-honeycomb-team", EncryptUtil.unscramble(params.get(PROPERTY_KEY_HONEYCOMB_APIKEY)));

        var metricsExporter = buildMetricsExporter(endpoint, params);

        return buildGrpcSpanProcessor(headers, endpoint, metricsExporter);
    }

    @Nullable
    private MetricExporter buildMetricsExporter(String endpoint, Map<String, String> params) {
        if (params.getOrDefault(PROPERTY_KEY_HONEYCOMB_METRICS_ENABLED, "false").equals("true")) {
            return OtlpGrpcMetricExporter.builder()
                    .setEndpoint(endpoint)
                    .addHeader("x-honeycomb-team", EncryptUtil.unscramble(params.get(PROPERTY_KEY_HONEYCOMB_APIKEY)))
                    .addHeader("x-honeycomb-dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET))
                    .build();
        }
        return null;
    }

    private SpanProcessor buildGrpcSpanProcessor(Map<String, String> headers, String exporterEndpoint, MetricExporter metricsExporter) {

        var serviceNameResource = Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, PluginConstants.SERVICE_NAME));
        var meterProvider = OTELMetrics.getOTELMeterProvider(metricsExporter, serviceNameResource);

        var spanExporterBuilder = OtlpGrpcSpanExporter.builder();
        headers.forEach(spanExporterBuilder::addHeader);
        var spanExporter = spanExporterBuilder
                .setEndpoint(exporterEndpoint)
                .setMeterProvider(meterProvider)
                .build();

        return BatchSpanProcessor.builder(spanExporter)
                .setMaxQueueSize(32768) // Default is 2048. Increasing it to limit dropped spans.
                .setScheduleDelay(Duration.ofSeconds(5)) // Default is 5s. This is another lever we can tweak.
                .setMaxExportBatchSize(8192) // Default is 512. Increasing it to limit dropped spans.
                .setMeterProvider(meterProvider)
                .build();
    }

    @Override
    public SetProjectConfigurationSettingsRequest getSetProjectConfigurationSettingsRequest(HttpServletRequest request) {
        return new SetHoneycombProjectConfigurationSettingsRequest(request);
    }

    @Override
    public void mapParamsToModel(Map<String, String> params, Map<String, Object> model) {
        model.put("otelEndpoint", params.get(PROPERTY_KEY_ENDPOINT));
        model.put("otelHoneycombTeam", params.get(PROPERTY_KEY_HONEYCOMB_TEAM));
        model.put("otelHoneycombDataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
        model.put("otelHoneycombMetricsEnabled", params.get(PROPERTY_KEY_HONEYCOMB_METRICS_ENABLED));
        if (params.get(PROPERTY_KEY_HONEYCOMB_APIKEY) == null) {
            model.put("otelHoneycombApiKey", null);
        }
        else {
            model.put("otelHoneycombApiKey", RSACipher.encryptDataForWeb(EncryptUtil.unscramble(params.get(PROPERTY_KEY_HONEYCOMB_APIKEY))));
        }
    }
}
