package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.server.endpoints.OTELEndpointFactory;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.SimpleView;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class ProjectConfigurationSettingsController extends BaseFormXmlController {
    @NotNull
    private final ProjectManager projectManager;
    static Logger LOG = Logger.getLogger(ProjectConfigurationSettingsController.class.getName());
    private final OTELEndpointFactory otelEndpointFactory;

    public ProjectConfigurationSettingsController(
            @NotNull ProjectManager projectManager,
            @NotNull WebControllerManager controllerManager,
            @NotNull OTELEndpointFactory otelEndpointFactory) {
        this.projectManager = projectManager;
        this.otelEndpointFactory = otelEndpointFactory;

        controllerManager.registerController("/admin/" + PLUGIN_NAME + "/settings.html", this);
    }

    @NotNull
    @Override
    protected ModelAndView doGet(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        return SimpleView.createTextView("Method is not supported!");
    }

    @Override
    protected void doPost(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Element xmlResponse) {
        ActionErrors errors = new ActionErrors();

        SProject project = projectManager.findProjectByExternalId(request.getParameter("projectId"));

        if (project == null) {
            errors.addError("projectId", String.format("Can't find project by given '%s' project id!", request.getParameter("projectId")));
            errors.serialize(xmlResponse);
            return;
        }

        var settingsRequest = mapRequest(request);
        if (!settingsRequest.validate(errors)) {
            errors.serialize(xmlResponse);
            return;
        }

        //todo: tell the OTELHelperFactory that project settings have changed.
        //      this would allow us to change the factory to cache based on project, rather than on build

        var feature = project.getOwnFeaturesOfType(PLUGIN_NAME);
        if (settingsRequest.mode.isPresent() && settingsRequest.mode.get().equals(SaveMode.RESET)) {
            if (!feature.isEmpty()) {
                project.removeFeature(feature.stream().findFirst().get().getId());
                project.persist();
                ActionMessages.getOrCreateMessages(request).addMessage("featureReset", "Feature was reset to the inherited settings.");
            } else {
                LOG.warn(String.format("Got a request to reset settings, but the settings didn't exist on project %s?", project.getProjectId()));
            }
        } else {
            if (feature.isEmpty()) {
                project.addFeature(PLUGIN_NAME, settingsRequest.AsParams());
            } else {
                var featureId = feature.stream().findFirst().get().getId();
                project.updateFeature(featureId, PLUGIN_NAME, settingsRequest.AsParams());
            }

            project.persist();

            ActionMessages.getOrCreateMessages(request).addMessage("featureUpdated", "Feature was updated.");
        }
    }

    private SetProjectConfigurationSettingsRequest mapRequest(HttpServletRequest request) {
        var otelHandler = otelEndpointFactory.getOTELEndpointHandler(request.getParameter("service"));
        return otelHandler.getSetProjectConfigurationSettingsRequest(request);
    }
}

