package com.octopus.teamcity.opentelemetry.server.helpers;

import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SRunningBuild;

public interface OTELHelperFactory {
    OTELHelper getOTELHelper(BuildPromotion build);

    void release(Long buildId);
}
