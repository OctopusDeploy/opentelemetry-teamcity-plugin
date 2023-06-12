package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.server.endpoints.OTELEndpointFactory;
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
    @NotNull
    private final ProjectManager projectManager;
    @NotNull
    private final OTELEndpointFactory otelEndpointFactory;

    public ProjectConfigurationTab(
            @NotNull PagePlaces pagePlaces,
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull ProjectManager projectManager,
            @NotNull OTELEndpointFactory otelEndpointFactory
        ) {
        super(pagePlaces, "Octopus.TeamCity.OpenTelemetry", "projectConfigurationSettings.jsp", "OpenTelemetry");
        this.pluginDescriptor = pluginDescriptor;
        this.projectManager = projectManager;
        this.otelEndpointFactory = otelEndpointFactory;

        register();
    }

    @Override
    public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
        super.fillModel(model, request);

        SProject project = getProject(request);

        var features = project.getAvailableFeaturesOfType(PLUGIN_NAME);
        model.put("publicKey", RSACipher.getHexEncodedPublicKey());

        if ((long) features.size() == 0) {
            model.put("isEnabled", false);
        }
        else {
            var feature = features.stream().findFirst().get();
            if (feature.getProjectId().equals(project.getProjectId())) {
                model.put("isInherited", false);
                if (features.size() > 1) {
                    model.put("isOverridden", true);
                    var projectId = features.stream().skip(1).findFirst().get().getProjectId();
                    var sourceProject = projectManager.findProjectByExternalId(projectId);
                    //todo: i think we should be referring to internal id here
                    if (sourceProject != null) {
                        model.put("overwritesInheritedFromProjectName", sourceProject.getName());
                    } else {
                        model.put("overwritesInheritedFromProjectName", "<unknown>");
                    }
                }
            } else {
                model.put("isInherited", true);
                var sourceProject = projectManager.findProjectByExternalId(feature.getProjectId());
                model.put("inheritedFromProjectName", sourceProject.getName());
                model.put("isOverridden", false);
            }
            var params = feature.getParameters();

            var service = otelEndpointFactory.getOTELEndpointHandler(params.get(PROPERTY_KEY_SERVICE));

            model.put("otelEnabled", params.get(PROPERTY_KEY_ENABLED));
            model.put("otelService", params.get(PROPERTY_KEY_SERVICE));

            service.mapParamsToModel(params, model);
        }
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
