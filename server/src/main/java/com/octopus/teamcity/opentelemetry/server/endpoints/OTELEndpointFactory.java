package com.octopus.teamcity.opentelemetry.server.endpoints;

import com.octopus.teamcity.opentelemetry.server.endpoints.custom.CustomOTELEndpointHandler;
import com.octopus.teamcity.opentelemetry.server.endpoints.honeycomb.HoneycombOTELEndpointHandler;
import com.octopus.teamcity.opentelemetry.server.endpoints.zipkin.ZipKinOTELEndpointHandler;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class OTELEndpointFactory {

    @NotNull
    private final PluginDescriptor pluginDescriptor;

    public OTELEndpointFactory(
        @NotNull PluginDescriptor pluginDescriptor)
    {
        this.pluginDescriptor = pluginDescriptor;
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
                return new HoneycombOTELEndpointHandler(pluginDescriptor);
            case ZIPKIN:
                return new ZipKinOTELEndpointHandler(pluginDescriptor);
            case CUSTOM:
                return new CustomOTELEndpointHandler(pluginDescriptor);
            default:
                throw new IllegalArgumentException("Invalid service name " + otelService);
        }
    }
}

