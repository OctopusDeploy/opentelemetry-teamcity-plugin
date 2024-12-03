package com.octopus.teamcity.opentelemetry.server.endpoints;

import com.octopus.teamcity.opentelemetry.server.SetProjectConfigurationSettingsRequest;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public interface IOTELEndpointHandler {
    ModelAndView getBuildOverviewModelAndView(SBuild build, Map<String, String> params, String traceId);

    Pair<SpanProcessor, SdkMeterProvider> buildSpanProcessorAndMeterProvider(BuildPromotion buildPromotion, String endpoint, Map<String, String> params);

    SetProjectConfigurationSettingsRequest getSetProjectConfigurationSettingsRequest(HttpServletRequest request);

    void mapParamsToModel(Map<String, String> params, Map<String, Object> model);
}
