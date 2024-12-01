package com.octopus.teamcity.opentelemetry.server.endpoints;

import com.octopus.teamcity.opentelemetry.server.endpoints.custom.CustomOTELEndpointHandler;
import com.octopus.teamcity.opentelemetry.server.endpoints.honeycomb.HoneycombOTELEndpointHandler;
import com.octopus.teamcity.opentelemetry.server.endpoints.zipkin.ZipkinOTELEndpointHandler;
import jetbrains.buildServer.serverSide.TeamCityNodes;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class OTELEndpointFactory {

    @NotNull
    private final PluginDescriptor pluginDescriptor;
    @NotNull
    private final TeamCityNodes teamcityNodesService;

    public OTELEndpointFactory(
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull TeamCityNodes teamcityNodesService)
    {
        this.pluginDescriptor = pluginDescriptor;
        this.teamcityNodesService = teamcityNodesService;
    }

    public IOTELEndpointHandler getOTELEndpointHandler(String otelService)
    {
        return getOTELEndpointHandler(OTELService.get(otelService).get());
    }

    public IOTELEndpointHandler getOTELEndpointHandler(OTELService otelService)
    {
        //todo: can we look this up automatically somehow
        switch (otelService)
        {
            case HONEYCOMB:
                return new HoneycombOTELEndpointHandler(pluginDescriptor, teamcityNodesService);
            case ZIPKIN:
                return new ZipkinOTELEndpointHandler(pluginDescriptor);
            case CUSTOM:
                return new CustomOTELEndpointHandler(pluginDescriptor);
            default:
                throw new IllegalArgumentException("Invalid service name " + otelService);
        }
    }
}

