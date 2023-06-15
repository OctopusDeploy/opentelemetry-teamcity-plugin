package com.octopus.teamcity.opentelemetry.server.endpoints.custom;

import com.octopus.teamcity.opentelemetry.server.HeaderDto;
import com.octopus.teamcity.opentelemetry.server.SetProjectConfigurationSettingsRequest;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.PROPERTY_KEY_HEADERS;

public class SetCustomProjectConfigurationSettingsRequest extends SetProjectConfigurationSettingsRequest {
    public SetCustomProjectConfigurationSettingsRequest(HttpServletRequest request) {
        super(request);
    }

    @Override
    protected void serviceSpecificValidate(ActionErrors errors) {
    }

    @Override
    protected void mapServiceSpecificParams(HashMap<String, String> params, ArrayList<HeaderDto> headers) {
        headers.forEach(x -> {
            var valueToSave = x.getType().equals("plaintext")
                    ? x.getValue()
                    : EncryptUtil.scramble(x.getValue());
            params.put(PROPERTY_KEY_HEADERS + "[" + x.getKey() + "]", valueToSave);
        });
    }
}
