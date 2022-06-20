package com.octopus.teamcity.opentelemetry.server.helpers;

import jetbrains.buildServer.serverSide.SRunningBuild;

public interface OTELHelperFactory {
    OTELHelper getOTELHelper(SRunningBuild build);

    void release(Long buildId);
}
