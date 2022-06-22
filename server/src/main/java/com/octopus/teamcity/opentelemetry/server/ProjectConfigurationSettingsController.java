package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.SimpleView;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class ProjectConfigurationSettingsController extends BaseFormXmlController {
    @NotNull
    private final ProjectManager projectManager;

    public ProjectConfigurationSettingsController(
            @NotNull ProjectManager projectManager,
            @NotNull WebControllerManager controllerManager) {
        this.projectManager = projectManager;

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

        var settingsRequest = new SetProjectConfigurationSettingsRequest(request);
        if (!settingsRequest.validate(errors)) {
            errors.serialize(xmlResponse);
            return;
        }

        //todo: tell the OTELHelperFactory that project settings have changed.
        //      this would allow us to change the factory to cache based on project, rather than on build

        var feature = project.getOwnFeaturesOfType(PLUGIN_NAME);
        if (settingsRequest.mode.isPresent() &&  settingsRequest.mode.get().equals(SaveMode.RESET)) {
            if (!feature.isEmpty()) {
                project.removeFeature(feature.stream().findFirst().get().getId());
                project.persist();
                getOrCreateMessages(request).addMessage("featureReset", "Feature was reset to the inherited settings.");
            } else {
                Loggers.SERVER.warn(String.format("OTEL_PLUGIN: Got a request to reset settings, but the settings didn't exist on project %s?", project.getProjectId()));
            }
        } else {
            if (feature.isEmpty()) {
                project.addFeature(PLUGIN_NAME, settingsRequest.AsParams());
            } else {
                var featureId = feature.stream().findFirst().get().getId();
                project.updateFeature(featureId, PLUGIN_NAME, settingsRequest.AsParams());
            }

            project.persist();

            //todo: figure out how to get these messages to show in the ui.
            getOrCreateMessages(request).addMessage("featureUpdated", "Feature was updated.");
        }
    }

    class SetProjectConfigurationSettingsRequest {
        private final String enabled;
        private final Optional<OTELService> service;
        private final String endpoint;
        private final Optional<SaveMode> mode;
        private final String honeycombTeam;
        private final String honeycombDataset;
        private final String honeycombApiKey;
        private final ArrayList<HeaderDto> headers;

        public SetProjectConfigurationSettingsRequest(HttpServletRequest request) {
            this.enabled = request.getParameter("enabled");
            this.service = OTELService.get(request.getParameter("service"));
            this.endpoint = request.getParameter("endpoint");
            this.mode = SaveMode.get(request.getParameter("mode"));
            this.honeycombTeam = request.getParameter("honeycombTeam");
            this.honeycombDataset = request.getParameter("honeycombDataset");
            this.honeycombApiKey = RSACipher.decryptWebRequestData(request.getParameter("encryptedHoneycombApiKey"));

            headers = new ArrayList<>();

            request.getParameterMap().forEach((headerName,headerValue) -> {
                if (headerName.startsWith("headerKey_")) {
                    var key = headerValue[0];
                    var suffix = headerName.substring("headerKey_".length());
                    var type = request.getParameter("headerType_" + suffix);
                    var value = type.equals("password")
                            ? RSACipher.decryptWebRequestData(request.getParameter("encryptedHeaderValue_" + suffix))
                            : request.getParameter("headerValue_" + suffix);
                    var header = new HeaderDto(key, value, type);
                    headers.add(header);
                }
            });
        }

        public boolean validate(@NotNull ActionErrors errors) {
            if (!this.mode.isPresent())
                errors.addError("mode", "Mode must be set!");
            else {
                if (!this.mode.get().equals(SaveMode.RESET) && !this.mode.get().equals(SaveMode.SAVE))
                    errors.addError("mode", "Mode must be set to either 'reset' or 'save'!");
                if (this.mode.get().equals(SaveMode.RESET))
                    return true; //short circuit the rest of the validation - all related to "save"
            }

            if (!this.service.isPresent())
                errors.addError("service", "Service must be set!");
            else {
                if (!this.service.get().equals(OTELService.HONEYCOMB) && !this.service.get().equals(OTELService.CUSTOM))
                    errors.addError("service", "Service must be set to either 'honeycomb.io' or 'custom'!");

                if (!this.service.get().equals(OTELService.HONEYCOMB)) {
                    if (StringUtil.isEmptyOrSpaces(this.honeycombTeam))
                        errors.addError("honeycombTeam", "Team must be set!");
                    if (StringUtil.isEmptyOrSpaces(this.honeycombDataset))
                        errors.addError("honeycombDataset", "Dataset must be set!");
                    if (StringUtil.isEmptyOrSpaces(this.honeycombApiKey))
                        errors.addError("honeycombApiKey", "ApiKey must be set!");
                }
            }

            if (StringUtil.isEmptyOrSpaces(this.endpoint)) {
                errors.addError("endpoint", "Endpoint must be set!");
            } else {
                if (!this.endpoint.startsWith("https://") && !this.endpoint.startsWith("http://"))
                    errors.addError("endpoint", "Endpoint must be a valid url!");
            }
            if (!StringUtil.isEmptyOrSpaces(enabled) && !enabled.equals("true") && !enabled.equals("false"))
                errors.addError("enabled", String.format("Enabled value %s was not set to true or false!", enabled));
            if (headers.stream().anyMatch(x -> StringUtil.isEmptyOrSpaces(x.getKey())))
                errors.addError("headers", "One or more header keys are empty!");
            if (headers.stream().anyMatch(x -> StringUtil.isEmptyOrSpaces(x.getValue())))
                errors.addError("headers", "One or more header values are empty!");

            return errors.hasNoErrors();
        }

        public HashMap<String, String> AsParams() {
            var params = new HashMap<String, String>();
            params.put(PROPERTY_KEY_ENABLED, enabled);
            if (service.isPresent())
                params.put(PROPERTY_KEY_SERVICE, service.get().getValue());
            else
                params.put(PROPERTY_KEY_SERVICE, OTELService.HONEYCOMB.getValue());

            params.put(PROPERTY_KEY_ENDPOINT, endpoint);
            if (!service.isPresent() || service.get().equals(OTELService.HONEYCOMB)) {
                params.put(PROPERTY_KEY_HONEYCOMB_DATASET, honeycombDataset);
                params.put(PROPERTY_KEY_HONEYCOMB_TEAM, honeycombTeam);
                params.put(PROPERTY_KEY_HONEYCOMB_APIKEY, EncryptUtil.scramble(honeycombApiKey));
            } else {
                headers.forEach(x -> {
                    var valueToSave = x.getType().equals("plaintext")
                            ? x.getValue()
                            : EncryptUtil.scramble(x.getValue());
                    params.put(PROPERTY_KEY_HEADERS + "[" + x.getKey() + "]", valueToSave);
                });
            }
            return params;
        }
    }
}
