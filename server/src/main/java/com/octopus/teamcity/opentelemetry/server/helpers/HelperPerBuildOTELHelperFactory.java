package com.octopus.teamcity.opentelemetry.server.helpers;

import com.octopus.teamcity.opentelemetry.server.LogMasker;
import com.octopus.teamcity.opentelemetry.server.OTELService;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HelperPerBuildOTELHelperFactory implements OTELHelperFactory {
    static Logger LOG = Logger.getLogger(HelperPerBuildOTELHelperFactory.class.getName());
    private final ConcurrentHashMap<Long, OTELHelper> otelHelpers;
    private final ProjectManager projectManager;

    public HelperPerBuildOTELHelperFactory(
        ProjectManager projectManager
    ) {
        this.projectManager = projectManager;
        LOG.debug("Creating HelperPerBuildOTELHelperFactory.");

        this.otelHelpers = new ConcurrentHashMap<>();
    }

    public OTELHelper getOTELHelper(BuildPromotion build) {
        var buildId = build.getId();
        LOG.debug(String.format("Getting OTELHelper for build %d.", buildId));

        return otelHelpers.computeIfAbsent(buildId, key -> {
            LOG.debug(String.format("Creating OTELHelper for build %d.", buildId));
            var projectId = build.getProjectExternalId();
            var project = projectManager.findProjectByExternalId(projectId);

            var features = project.getAvailableFeaturesOfType(PLUGIN_NAME);
            if (!features.isEmpty()) {
                var feature = features.stream().findFirst().get();
                var params = feature.getParameters();
                if (params.get(PROPERTY_KEY_ENABLED).equals("true")) {
                    var endpoint = params.get(PROPERTY_KEY_ENDPOINT);
                    SpanProcessor spanProcessor;
                    if (params.get(PROPERTY_KEY_SERVICE).equals(OTELService.HONEYCOMB.getValue())) {
                        Map<String, String> headers = new HashMap<>();
                        headers.put("x-honeycomb-dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
                        headers.put("x-honeycomb-team", EncryptUtil.unscramble(params.get(PROPERTY_KEY_HONEYCOMB_APIKEY)));
                        spanProcessor = buildGrpcSpanProcessor(headers, endpoint);
                    } else if (params.get(PROPERTY_KEY_SERVICE).equals(OTELService.ZIPKIN.getValue())) {
                        spanProcessor = buildZipkinSpanProcessor(endpoint);
                    } else {
                        Map<String, String> headers = new HashMap<>();
                        params.forEach((k, v) -> {
                            if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                                var name = k.substring(PROPERTY_KEY_HEADERS.length());
                                name = name.substring(1, name.length() - 1);
                                var value = EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v;
                                headers.put(name, value);
                            }
                        });
                        spanProcessor = buildGrpcSpanProcessor(headers, endpoint);
                    }
                    long startTime = System.nanoTime();
                    var otelHelper = new OTELHelperImpl(spanProcessor);
                    long endTime = System.nanoTime();

                    long duration = (endTime - startTime);
                    LOG.debug(String.format("Created OTELHelper for build %d in %d milliseconds.", buildId, duration / 1000000));

                    return otelHelper;
                }
            }
            LOG.debug(String.format("Using NullOTELHelper for build %d.", buildId));
            return new NullOTELHelperImpl();
        });
    }

    private SpanProcessor buildZipkinSpanProcessor(String exporterEndpoint) {
        String endpoint = String.format("%s/api/v2/spans", exporterEndpoint);
        ZipkinSpanExporter zipkinExporter = ZipkinSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();

        return BatchSpanProcessor.builder(zipkinExporter).build();
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

    @Override
    public void release(Long buildId) {
        var helper = otelHelpers.get(buildId);
        if (helper != null) {
            helper.release();
            otelHelpers.remove(buildId);
        }
    }
}
