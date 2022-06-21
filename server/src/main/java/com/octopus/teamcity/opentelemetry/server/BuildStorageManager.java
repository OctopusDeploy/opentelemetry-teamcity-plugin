package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class BuildStorageManager {

    public static final String OTEL_TRACE_ID_FILENAME = "otel-trace-id";

    @Nullable
    public String getTraceId(SBuild build) {
        File artifactsDir = build.getArtifactsDirectory();
        File pluginFile = new File(artifactsDir, jetbrains.buildServer.ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + File.separatorChar + OTEL_TRACE_ID_FILENAME);

        Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Reading trace id or build %d.", build.getBuildId()));

        if (!pluginFile.exists()) {
            Loggers.SERVER.info(String.format("OTEL_PLUGIN: Unable to find build artifact %s for build %d.", OTEL_TRACE_ID_FILENAME, build.getBuildId()));
            return null;
        }

        String traceId;
        try {
            var fileReader = new Scanner(pluginFile);
            traceId = fileReader.nextLine();
            fileReader.close();
            Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Retrieved trace id %s for build %d.", traceId, build.getBuildId()));
        } catch (FileNotFoundException e) {
            Loggers.SERVER.warn(String.format("OTEL_PLUGIN: Unable to find trace id data file for build %d.", build.getBuildId()));
            return null;
        }
        return traceId;
    }

    public void saveTraceId(SRunningBuild build, String traceId) {
        File artifactsDir = build.getArtifactsDirectory();
        File pluginFile = new File(artifactsDir, jetbrains.buildServer.ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + File.separatorChar + OTEL_TRACE_ID_FILENAME);
        try {
            Loggers.SERVER.debug(String.format("OTEL_PLUGIN: Saving trace id %s for build %d.", traceId, build.getBuildId()));
            if (pluginFile.createNewFile()){
                var fileWriter = new FileWriter(pluginFile);
                fileWriter.write(traceId);
                fileWriter.close();
            }
        } catch (IOException e) {
            Loggers.SERVER.warn(String.format("OTEL_PLUGIN: Error trying to save trace id for build %d.", build.getBuildId()));
        }
    }
}
