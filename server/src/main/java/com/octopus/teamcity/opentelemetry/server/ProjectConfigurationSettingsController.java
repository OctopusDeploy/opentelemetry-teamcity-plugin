package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.SimpleView;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.commons.validator.routines.UrlValidator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

/**
 * User: g.chernyshev
 * Date: 14/11/16
 * Time: 22:40
 */
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

        if (settingsRequest.mode.equals("reset")) {
            var feature = project.getOwnFeaturesOfType(PLUGIN_NAME);
            if (!feature.isEmpty()) {
                project.removeFeature(feature.stream().findFirst().get().getId());
            }
            project.persist();
            getOrCreateMessages(request).addMessage("featureReset", "Feature was reset to the inherited settings.");
        } else {
            var feature = project.getOwnFeaturesOfType(PLUGIN_NAME);
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
        private final String service;
        private final String endpoint;
        private final String mode; //todo: change to enum
        private final String honeycombTeam;
        private final String honeycombDataset;
        private final ArrayList<HeaderDto> headers;

        public SetProjectConfigurationSettingsRequest(HttpServletRequest request) {
            this.enabled = request.getParameter("enabled");
            this.service = request.getParameter("service");
            this.endpoint = request.getParameter("endpoint");
            this.mode = request.getParameter("mode");
            this.honeycombTeam = request.getParameter("honeycombTeam");
            this.honeycombDataset = request.getParameter("honeycombDataset");

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
            if (StringUtil.isEmptyOrSpaces(this.mode)) {
                errors.addError("mode", "Mode must be set!");
            }
            if (!this.mode.equals("reset") && !this.mode.equals("save")) {
                errors.addError("mode", "Mode must be set to either 'reset' or 'save'!");
            }
            if (this.mode.equals("reset"))
                return true;

            if (StringUtil.isEmptyOrSpaces(this.service)) {
                errors.addError("service", "Service must be set!");
            }
            if (!this.service.equals("honeycomb.io") && !this.service.equals("custom")) {
                errors.addError("service", "Service must be set to either 'honeycomb.io' or 'custom'!");
            }

            if (!this.service.equals("honeycomb.io")) {
                if (StringUtil.isEmptyOrSpaces(this.honeycombTeam)) {
                    errors.addError("honeycombTeam", "Team must be set!");
                }
                if (StringUtil.isEmptyOrSpaces(this.honeycombDataset)) {
                    errors.addError("honeycombDataset", "Dataset must be set!");
                }
            }

            if (StringUtil.isEmptyOrSpaces(this.endpoint)) {
                errors.addError("endpoint", "Endpoint must be set!");
            } else {
                var urlValidator = new UrlValidator(new String[] {"http", "https"});
                if (!urlValidator.isValid(this.endpoint)) {
                    errors.addError("endpoint", "Endpoint must be a valid url!");
                }
            }
            if (!StringUtil.isEmptyOrSpaces(enabled) && !enabled.equals("true") && !enabled.equals("false")) {
                errors.addError("enabled", String.format("Enabled value %s was not set to true or false!", enabled));
            }
            if (headers.stream().anyMatch(x -> StringUtil.isEmptyOrSpaces(x.getKey()))) {
                errors.addError("headers", "One or more header keys are empty!");
            }
            if (headers.stream().anyMatch(x -> StringUtil.isEmptyOrSpaces(x.getValue()))) {
                errors.addError("headers", "One or more header values are empty!");
            }

            return errors.hasNoErrors();
        }

        public HashMap<String, String> AsParams() {
            var params = new HashMap<String, String>();
            params.put(PROPERTY_KEY_ENABLED, enabled);
            params.put(PROPERTY_KEY_SERVICE, service);
            params.put(PROPERTY_KEY_ENDPOINT, endpoint);
            params.put(PROPERTY_KEY_HONEYCOMB_DATASET, honeycombDataset);
            params.put(PROPERTY_KEY_HONEYCOMB_TEAM, honeycombTeam);

            headers.forEach(x -> {
                var valueToSave = x.getType().equals("plaintext")
                    ? x.getValue()
                    : EncryptUtil.scramble(x.getValue());
                params.put(PROPERTY_KEY_HEADERS + "[" + x.getKey() + "]", valueToSave);
            });

            return params;
        }
    }
}
