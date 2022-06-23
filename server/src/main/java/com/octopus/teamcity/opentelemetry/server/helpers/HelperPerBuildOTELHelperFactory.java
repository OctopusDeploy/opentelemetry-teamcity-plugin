package com.octopus.teamcity.opentelemetry.server.helpers;

import com.octopus.teamcity.opentelemetry.server.OTELService;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import org.apache.log4j.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HelperPerBuildOTELHelperFactory implements OTELHelperFactory {
    private final ConcurrentHashMap<Long, OTELHelper> otelHelpers;
    private final ProjectManager projectManager;

    public HelperPerBuildOTELHelperFactory(
    ) {
        this.projectManager = projectManager;
        Loggers.SERVER.debug("OTEL_PLUGIN: Creating HelperPerBuildOTELHelperFactory.");

        this.otelHelpers = new ConcurrentHashMap<>();
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
                    if (params.get(PROPERTY_KEY_SERVICE).equals(OTELService.HONEYCOMB.getValue())) {
                        headers.put("x-honeycomb-dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
                        headers.put("x-honeycomb-team", EncryptUtil.unscramble(params.get(PROPERTY_KEY_HONEYCOMB_APIKEY)));
                    } else {
                        params.forEach((k, v) -> {
                            if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                                var name = k.substring(PROPERTY_KEY_HEADERS.length());
                                name = name.substring(1, name.length() - 1);
                                var value = EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v;
                                headers.put(name, value);
                            }
                        });
                    }
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
