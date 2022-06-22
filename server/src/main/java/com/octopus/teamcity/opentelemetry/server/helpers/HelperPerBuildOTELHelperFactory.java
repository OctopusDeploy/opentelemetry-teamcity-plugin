package com.octopus.teamcity.opentelemetry.server.helpers;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HelperPerBuildOTELHelperFactory implements OTELHelperFactory {
    private final Map<Long, OTELHelper> otelHelpers;
    private final ProjectManager projectManager;
    private final SBuildServer sBuildServer;

    public HelperPerBuildOTELHelperFactory(
            ProjectManager projectManager,
            SBuildServer sBuildServer
    ) {
        this.projectManager = projectManager;
        this.sBuildServer = sBuildServer;
        Loggers.SERVER.debug("OTEL_PLUGIN: Creating HelperPerBuildOTELHelperFactory.");

        this.otelHelpers = Collections.synchronizedMap(new HashMap<>());
    }

    public OTELHelper getOTELHelper(BuildPromotion build) {
        var buildId = build.getId();
        Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Getting OTELHelper for build %d.", buildId));
        return otelHelpers.computeIfAbsent(buildId, key -> {
            Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Creating OTELHelper for build %d.", buildId));
            var projectId = build.getProjectExternalId();
            var project = projectManager.findProjectByExternalId(projectId);

            var features = project.getAvailableFeaturesOfType(PLUGIN_NAME);
            if (!features.isEmpty()) {
                var feature = features.stream().findFirst().get();
                var params = feature.getParameters();
                if (params.get(PROPERTY_KEY_ENABLED).equals("true")) {
                    var endpoint = params.get(PROPERTY_KEY_ENDPOINT);
                    Map<String, String> headers = new HashMap<>();
                    params.forEach((k, v) -> {
                        if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                            var name = k.substring(PROPERTY_KEY_HEADERS.length());
                            name = name.substring(1, name.length() - 1);
                            var value = EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v;
                            headers.put(name, value);
                        }
                    });
                    long startTime = System.nanoTime();
                    var otelHelper = new OTELHelperImpl(headers, endpoint);
                    long endTime = System.nanoTime();

                    long duration = (endTime - startTime);
                    Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Created OTELHelper for build %d in %d milliseconds.", buildId, duration / 1000000));

                    return otelHelper;
                }
            }
            Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Using NullOTELHelper for build %d.", buildId));
            return new NullOTELHelperImpl();
        });
    }

    @Override
    public void release(Long buildId) {
        otelHelpers.remove(buildId);
    }
}
