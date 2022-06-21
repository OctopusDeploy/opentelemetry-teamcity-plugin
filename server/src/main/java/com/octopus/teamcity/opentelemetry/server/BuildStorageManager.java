package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;

import javax.annotation.Nullable;

public interface BuildStorageManager {
    @Nullable
    String getTraceId(SBuild build);

    void saveTraceId(SRunningBuild build, String traceId);
}
