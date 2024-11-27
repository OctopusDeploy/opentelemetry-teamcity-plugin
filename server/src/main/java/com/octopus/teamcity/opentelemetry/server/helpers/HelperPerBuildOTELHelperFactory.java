package com.octopus.teamcity.opentelemetry.server.helpers;

import com.octopus.teamcity.opentelemetry.server.endpoints.OTELEndpointFactory;
import io.opentelemetry.sdk.trace.SpanProcessor;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManager;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HelperPerBuildOTELHelperFactory implements OTELHelperFactory {
    static Logger LOG = Logger.getLogger(HelperPerBuildOTELHelperFactory.class.getName());
    private final ConcurrentHashMap<Long, OTELHelper> otelHelpers;
    private final ProjectManager projectManager;
    @NotNull
    private final OTELEndpointFactory otelEndpointFactory;

    public HelperPerBuildOTELHelperFactory(
        ProjectManager projectManager,
        @NotNull OTELEndpointFactory otelEndpointFactory
    ) {
        this.projectManager = projectManager;
        this.otelEndpointFactory = otelEndpointFactory;
        LOG.debug("Creating HelperPerBuildOTELHelperFactory.");

        this.otelHelpers = new ConcurrentHashMap<>();
    }

    public OTELHelper getOTELHelper(BuildPromotion build) {
        var buildId = build.getId();

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
                    var otelHandler = otelEndpointFactory.getOTELEndpointHandler(params.get(PROPERTY_KEY_SERVICE));
                    spanProcessor = otelHandler.buildSpanProcessor(endpoint, params);

                    long startTime = System.nanoTime();
                    var otelHelper = new OTELHelperImpl(spanProcessor, String.valueOf(buildId));
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

    @Override
    public void release(Long buildId) {
        var helper = otelHelpers.get(buildId);
        if (helper != null) {
            helper.release(String.valueOf(buildId));
            otelHelpers.remove(buildId);
        }
    }
}
