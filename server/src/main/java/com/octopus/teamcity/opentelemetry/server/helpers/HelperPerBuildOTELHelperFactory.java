package com.octopus.teamcity.opentelemetry.server.helpers;

import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class HelperPerBuildOTELHelperFactory implements OTELHelperFactory {
    private final Map<Long, OTELHelper> otelHelpers;
    private ProjectManager projectManager;

    public HelperPerBuildOTELHelperFactory(ProjectManager projectManager) {
        this.projectManager = projectManager;
        this.otelHelpers = Collections.synchronizedMap(new HashMap<>());
    }

    public OTELHelper getOTELHelper(SRunningBuild build) {
        return otelHelpers.computeIfAbsent(build.getBuildId(), key -> {
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
                            ;
                            var value = EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v;
                            headers.put(name, value);
                        }
                    });
                    return new OTELHelperImpl(headers, endpoint);
                }
            }
            return new NullOTELHelperImpl();
        });
    }

    @Override
    public void release(Long buildId) {
        otelHelpers.remove(buildId);
    }
}
