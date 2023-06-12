package com.octopus.teamcity.opentelemetry.server.endpoints;

import com.octopus.teamcity.opentelemetry.server.HeaderDto;
import com.octopus.teamcity.opentelemetry.server.ProjectConfigurationSettingsController;
import com.octopus.teamcity.opentelemetry.server.SetProjectConfigurationSettingsRequest;
import io.opentelemetry.sdk.trace.SpanProcessor;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.serverSide.SBuild;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface IOTELEndpointHandler {
    ModelAndView getBuildOverviewModelAndView(SBuild build, Map<String, String> params, String traceId);

    SpanProcessor BuildSpanProcessor(String endpoint, Map<String, String> params);

    SetProjectConfigurationSettingsRequest GetSetProjectConfigurationSettingsRequest(HttpServletRequest request);
}
