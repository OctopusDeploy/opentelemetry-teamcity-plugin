package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelper;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelperFactory;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SRunningBuild;

public class TestOTELHelperFactory implements OTELHelperFactory {
    private OTELHelper otelHelper;

    public TestOTELHelperFactory(OTELHelper otelHelper) {
        this.otelHelper = otelHelper;
    }

    @Override
    public OTELHelper getOTELHelper(BuildPromotion build) {
        return otelHelper;
    }

    @Override
    public void release(Long buildId) {
    }
}
