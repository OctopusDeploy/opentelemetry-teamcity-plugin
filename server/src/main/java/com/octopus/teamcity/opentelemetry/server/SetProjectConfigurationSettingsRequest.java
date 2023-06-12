package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.server.endpoints.OTELService;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public abstract class SetProjectConfigurationSettingsRequest {
    private final String enabled;
    private final Optional<OTELService> service;
    private final String endpoint;
    final Optional<SaveMode> mode;

    private final ArrayList<HeaderDto> headers;

    public SetProjectConfigurationSettingsRequest(HttpServletRequest request) {
        this.enabled = request.getParameter("enabled");
        this.service = OTELService.get(request.getParameter("service"));
        this.endpoint = request.getParameter("endpoint");
        this.mode = SaveMode.get(request.getParameter("mode"));

        headers = new ArrayList<>();

        request.getParameterMap().forEach((headerName, headerValue) -> {
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
            errors.addError("service", "Service must be set to one of " + OTELService.readableJoin());
        else {
            serviceSpecificValidate(errors);
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
            params.put(PROPERTY_KEY_SERVICE, OTELService.getDefault().getValue());

        params.put(PROPERTY_KEY_ENDPOINT, endpoint);

        mapServiceSpecificParams(params, headers);

        return params;
    }

    protected abstract void serviceSpecificValidate(ActionErrors errors);

    protected abstract void mapServiceSpecificParams(HashMap<String, String> params, ArrayList<HeaderDto> headers);
}
