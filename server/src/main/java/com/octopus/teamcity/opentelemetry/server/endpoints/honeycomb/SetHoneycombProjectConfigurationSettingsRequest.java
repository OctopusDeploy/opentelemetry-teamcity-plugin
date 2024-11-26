package com.octopus.teamcity.opentelemetry.server.endpoints.honeycomb;

import com.octopus.teamcity.opentelemetry.server.HeaderDto;
import com.octopus.teamcity.opentelemetry.server.SetProjectConfigurationSettingsRequest;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.util.StringUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class SetHoneycombProjectConfigurationSettingsRequest extends SetProjectConfigurationSettingsRequest {
    public final String honeycombTeam;
    public final String honeycombDataset;
    public final String honeycombApiKey;
    public final String honeycombMetricsEnabled;

    public SetHoneycombProjectConfigurationSettingsRequest(HttpServletRequest request) {
        super(request);
        this.honeycombTeam = request.getParameter("honeycombTeam");
        this.honeycombDataset = request.getParameter("honeycombDataset");
        this.honeycombMetricsEnabled = request.getParameter("honeycombMetricsEnabled");
        this.honeycombApiKey = RSACipher.decryptWebRequestData(request.getParameter("encryptedHoneycombApiKey"));
    }

    @Override
    protected void serviceSpecificValidate(ActionErrors errors) {
        if (StringUtil.isEmptyOrSpaces(honeycombTeam))
            errors.addError("honeycombTeam", "Team must be set!");
        if (StringUtil.isEmptyOrSpaces(honeycombDataset))
            errors.addError("honeycombDataset", "Dataset must be set!");
        if (StringUtil.isEmptyOrSpaces(honeycombApiKey))
            errors.addError("honeycombApiKey", "ApiKey must be set!");
    }

    @Override
    protected void mapServiceSpecificParams(HashMap<String, String> params, ArrayList<HeaderDto> headers) {
        params.put(PROPERTY_KEY_HONEYCOMB_DATASET, honeycombDataset);
        params.put(PROPERTY_KEY_HONEYCOMB_TEAM, honeycombTeam);
        params.put(PROPERTY_KEY_HONEYCOMB_METRICS_ENABLED, honeycombMetricsEnabled);
        params.put(PROPERTY_KEY_HONEYCOMB_APIKEY, EncryptUtil.scramble(honeycombApiKey));
    }
}
