package com.octopus.teamcity.opentelemetry.server.endpoints.honeycomb;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import com.octopus.teamcity.opentelemetry.server.*;
import com.octopus.teamcity.opentelemetry.server.endpoints.IOTELEndpointHandler;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jetbrains.buildServer.serverSide.BuildPromotion;
import io.opentelemetry.semconv.ServiceAttributes;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HoneycombOTELEndpointHandler implements IOTELEndpointHandler {

    private final PluginDescriptor pluginDescriptor;
    private final TeamCityNodes nodesService;
    static Logger LOG = Logger.getLogger(HoneycombOTELEndpointHandler.class.getName());

    public HoneycombOTELEndpointHandler(PluginDescriptor pluginDescriptor, TeamCityNodes nodesService) {
        this.pluginDescriptor = pluginDescriptor;
        this.nodesService = nodesService;
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
    public Pair<SpanProcessor, SdkMeterProvider> buildSpanProcessorAndMeterProvider(BuildPromotion buildPromotion, String endpoint, Map<String, String> params) {
        Map<String, String> headers = new HashMap<>();
        //todo: add a setting to say "use classic" or "use environments"
        headers.put("x-honeycomb-dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
        headers.put("x-honeycomb-team", EncryptUtil.unscramble(params.get(PROPERTY_KEY_HONEYCOMB_APIKEY)));

        var metricsExporter = buildMetricsExporter(endpoint, params);

        return buildGrpcSpanProcessor(buildPromotion, headers, endpoint, metricsExporter);
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

    private Pair<SpanProcessor, SdkMeterProvider> buildGrpcSpanProcessor(
            BuildPromotion buildPromotion,
            Map<String, String> headers,
            String exporterEndpoint,
            @Nullable MetricExporter metricsExporter) {

        //todo: centralise the definition of this
        var serviceNameResource = Resource
                .create(Attributes.of(
                        ServiceAttributes.SERVICE_NAME, PluginConstants.SERVICE_NAME,
                        AttributeKey.stringKey("teamcity.build_promotion.id"), Long.toString(buildPromotion.getId()),
                        AttributeKey.stringKey("teamcity.node.id"), nodesService.getCurrentNode().getId()
                ));
        var meterProvider = OTELMetrics.getOTELMeterProvider(metricsExporter, serviceNameResource);

        var spanExporterBuilder = OtlpGrpcSpanExporter.builder();
        spanExporterBuilder.setEndpoint(exporterEndpoint);
        headers.forEach(spanExporterBuilder::addHeader);
        if (meterProvider != null) {
            spanExporterBuilder.setMeterProvider(meterProvider);
        }
        var spanExporter = spanExporterBuilder.build();

        var batchSpanProcessorBuilder = BatchSpanProcessor.builder(spanExporter);
        batchSpanProcessorBuilder.setMaxQueueSize(BATCH_SPAN_PROCESSOR_MAX_QUEUE_SIZE);
        batchSpanProcessorBuilder.setScheduleDelay(BATCH_SPAN_PROCESSOR_MAX_SCHEDULE_DELAY);
        batchSpanProcessorBuilder.setMaxExportBatchSize(BATCH_SPAN_PROCESSOR_MAX_EXPORT_BATCH_SIZE);
        if (meterProvider != null) {
            batchSpanProcessorBuilder.setMeterProvider(meterProvider);
        }
        return Pair.of(batchSpanProcessorBuilder.build(), meterProvider);
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
