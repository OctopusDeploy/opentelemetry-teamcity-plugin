package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.jetbrains.annotations.Nullable;

public class TestBuildStorageManager implements BuildStorageManager {
    @Nullable
    @Override
    public String getTraceId(SBuild build) {
        return "1234";
    }

    @Override
    public void saveTraceId(SRunningBuild build, String traceId) {
    }
}
