package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.log.Loggers;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class BuildStorageManagerImpl implements BuildStorageManager {
    static Logger LOG = Logger.getLogger(BuildStorageManagerImpl.class.getName());
    public static final String OTEL_TRACE_ID_FILENAME = "otel-trace-id";

    @Override
    @Nullable
    public String getTraceId(SBuild build) {
        File artifactsDir = build.getArtifactsDirectory();
        File pluginFile = new File(artifactsDir, jetbrains.buildServer.ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + File.separatorChar + OTEL_TRACE_ID_FILENAME);

        LOG.debug(String.format("Reading trace id for build %d.", build.getBuildId()));

        if (!pluginFile.exists()) {
            LOG.info(String.format("Unable to find build artifact %s for build %d.", OTEL_TRACE_ID_FILENAME, build.getBuildId()));
            return null;
        }

        String traceId;
        try (Scanner fileReader = new Scanner(pluginFile)) {
            traceId = fileReader.nextLine();
            LOG.debug(String.format("Retrieved trace id %s for build %d.", traceId, build.getBuildId()));
        } catch (FileNotFoundException e) {
            LOG.warn(String.format("Unable to find trace id data file for build %d.", build.getBuildId()));
            return null;
        }
        return traceId;
    }

    @Override
    public void saveTraceId(SRunningBuild build, String traceId) {
        IOGuard.allowDiskWrite(() -> {
            File artifactsDir = build.getArtifactsDirectory();
            File pluginFile = new File(artifactsDir, jetbrains.buildServer.ArtifactsConstants.TEAMCITY_ARTIFACTS_DIR + File.separatorChar + OTEL_TRACE_ID_FILENAME);
            LOG.debug(String.format("Saving trace id %s to %s for build %d.", traceId, OTEL_TRACE_ID_FILENAME, build.getBuildId()));
            try (FileWriter fileWriter = new FileWriter(pluginFile)) {
                fileWriter.write(traceId);
            } catch (IOException e) {
                LOG.warn(String.format("Error trying to save trace id for build %d.", build.getBuildId()));
            }
        });
    }
}
