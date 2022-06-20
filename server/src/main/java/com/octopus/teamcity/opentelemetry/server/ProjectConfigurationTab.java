package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.controllers.admin.projects.EditProjectTab;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class ProjectConfigurationTab extends EditProjectTab {
    @NotNull
    private final PluginDescriptor pluginDescriptor;
    private ProjectManager projectManager;

    public ProjectConfigurationTab(
            @NotNull PagePlaces pagePlaces,
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull ProjectManager projectManager
        ) {
        super(pagePlaces, "Octopus.TeamCity.OpenTelemetry", "projectConfigurationSettings.jsp", "OpenTelemetry");
        this.pluginDescriptor = pluginDescriptor;
        this.projectManager = projectManager;

        register();
    }

    @Override
    public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
        super.fillModel(model, request);

        SProject project = getProject(request);

        var features = project.getAvailableFeaturesOfType(PLUGIN_NAME);

        if ((long) features.size() == 0) {
            model.put("isEnabled", false);
        }
        else {
            var feature = features.stream().findFirst().get();
            if (feature.getProjectId().equals(project.getProjectId())) {
                model.put("isInherited", false);
                if (features.size() > 1) {
                    model.put("isOverridden", true);
                    var sourceProject = projectManager.findProjectByExternalId(features.stream().skip(1).findFirst().get().getProjectId());
                    model.put("overwritesInheritedFromProjectName", sourceProject.getName());
                }
            } else {
                model.put("isInherited", true);
                var sourceProject = projectManager.findProjectByExternalId(feature.getProjectId());
                model.put("inheritedFromProjectName", sourceProject.getName());
                model.put("isOverridden", false);
            }
            var params = feature.getParameters();
            model.put("otelEnabled", params.get(PROPERTY_KEY_ENABLED));
            model.put("otelService", params.get(PROPERTY_KEY_SERVICE));
            model.put("otelEndpoint", params.get(PROPERTY_KEY_ENDPOINT));

            var headers = new ArrayList<HeaderDto>();
            params.forEach((k,v)->{
                if (k.startsWith(PROPERTY_KEY_HEADERS)) {
                    var key = k.substring(PROPERTY_KEY_HEADERS.length());
                    key = key.substring(1, key.length() - 1);;
                    var header = new HeaderDto(key,
                            EncryptUtil.isScrambled(v) ? EncryptUtil.unscramble(v) : v,
                            EncryptUtil.isScrambled(v) ? "password" : "plaintext");
                    headers.add(header);
                }
            });

            model.put("otelHeaders", headers);
            model.put("publicKey", RSACipher.getHexEncodedPublicKey());
        }
    }

    @NotNull
    @Override
    public String getTabTitle(@NotNull HttpServletRequest request) {
        SProject project = this.getProject(request);
        String tabTitle = super.getTabTitle(request);

        if (project != null) {
            int count = 0;
//            Map<SProject, List<AgentPriorityDescriptor>> priorities = priorityManager.configuredPerProject(project);
//            for (List<AgentPriorityDescriptor> descriptors : priorities.values()) {
//                count += descriptors.size();
//            }

            if (count > 0) {
                tabTitle += String.format(" (%d)", count);
            }
        }

        return tabTitle;
    }

    @NotNull
    @Override
    public List<String> getJsPaths() {
        return Arrays.asList(
            pluginDescriptor.getPluginResourcesPath("projectConfigurationSettings.js")
        );
    }

    @NotNull
    @Override
    public List<String> getCssPaths() {
        return Arrays.asList(
                pluginDescriptor.getPluginResourcesPath("projectConfigurationSettings.css")
        );
    }
}

