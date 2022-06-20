package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelper;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelperFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.buildLog.BlockLogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessageFilter;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TeamCityBuildListener extends BuildServerAdapter {

    private final ConcurrentHashMap<String, Long> checkoutTimeMap;
    private OTELHelperFactory otelHelperFactory;
    private BuildStorageManager buildStorageManager;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public TeamCityBuildListener(
        EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher,
        OTELHelperFactory otelHelperFactory,
        BuildStorageManager buildStorageManager
    ) {
        this.otelHelperFactory = otelHelperFactory;
        this.buildStorageManager = buildStorageManager;
        this.checkoutTimeMap = new ConcurrentHashMap<>();
        buildServerListenerEventDispatcher.addListener(this);
        Loggers.SERVER.info("OTEL_PLUGIN: BuildListener registered.");
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        if (buildListenerReady(build)) {
            var otelHelper = otelHelperFactory.getOTELHelper(build);
            Loggers.SERVER.debug("OTEL_PLUGIN: Build started method triggered for " + getBuildName(build));

            String parentBuildId = getParentBuild(build);
            Span parentSpan = otelHelper.getParentSpan(parentBuildId);
            Span span = otelHelper.createSpan(getBuildId(build), parentSpan);
            Loggers.SERVER.debug("OTEL_PLUGIN: Span created for " + getBuildName(build));

            buildStorageManager.saveTraceId(build, span.getSpanContext().getTraceId());

            try (Scope ignored = parentSpan.makeCurrent()) {
                setSpanBuildAttributes(otelHelper, build, span, getBuildName(build), build.getBuildTypeExternalId());
                span.addEvent(PluginConstants.EVENT_STARTED);
                Loggers.SERVER.debug("OTEL_PLUGIN: " + PluginConstants.EVENT_STARTED + " event added to span for build " + getBuildName(build));
            } catch (Exception e) {
                Loggers.SERVER.error("OTEL_PLUGIN: Exception in Build Start caused by: " + e + e.getCause() +
                        ", with message: " + e.getMessage() +
                        ", and stacktrace: " + Arrays.toString(e.getStackTrace()));
                if (span != null) {
                    span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_START + ": " + e.getMessage());
                }
            }
        } else {
            Loggers.SERVER.info("OTEL_PLUGIN: Build start triggered for " +  getBuildName(build) + " and plugin not ready. This build will not be traced.");
        }
    }

    private boolean buildListenerReady(SRunningBuild build) {
        var helper = otelHelperFactory.getOTELHelper(build);
        return helper.isReady();
    }

    private String getBuildId(SRunningBuild build) {
        return String.valueOf(build.getBuildId());
    }

    private String getBuildName(SRunningBuild build) {
        return build.getBuildType() != null ? build.getBuildType().getName() : "unknown_build_name";
    }

    private String getParentBuild(SRunningBuild build) {
        BuildPromotion[] parentBuilds = build.getBuildPromotion().findTops();
        BuildPromotion parentBuildPromotion = parentBuilds[0];
        Loggers.SERVER.debug("OTEL_PLUGIN: Top Build Parent: " + parentBuildPromotion);
        Loggers.SERVER.debug("OTEL_PLUGIN: Top Build Parent Id: " + parentBuildPromotion.getId());
        return String.valueOf(parentBuildPromotion.getId());
    }

    private void setSpanBuildAttributes(OTELHelper otelHelper, SRunningBuild build, Span span, String spanName, String serviceName) {
        if (build.getBuildType() != null) {
            otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_PROJECT_NAME, build.getBuildType().getProject().getName());
        }
        if (build.getBranch() != null ) {
            otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BRANCH,  build.getBranch().getName());
        }
        if (!build.getRevisions().isEmpty()) {
            otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_COMMIT,  build.getRevisions().iterator().next().getRevisionDisplayName());
        }
        if (build.getProjectExternalId() != null ) {
            otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_PROJECT_ID, build.getProjectExternalId());
        }
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_TYPE_ID, build.getBuildTypeId());
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_TYPE_EXTERNAL_ID, build.getBuildTypeExternalId());
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_AGENT_NAME, build.getAgentName());
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_AGENT_TYPE, build.getAgent().getAgentTypeId());
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_NUMBER, build.getBuildNumber());
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_SERVICE_NAME,  serviceName);
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_NAME, spanName);
    }

    @Override
    public void buildFinished(@NotNull SRunningBuild build) {
        super.buildFinished(build);
        if (buildListenerReady(build)) {
            buildFinishedOrInterrupted(build);
        }
    }

    @Override
    public void buildInterrupted(@NotNull SRunningBuild build) {
        super.buildInterrupted(build);
        if (buildListenerReady(build)) {
            buildFinishedOrInterrupted(build);
        }
    }

    private void buildFinishedOrInterrupted (SRunningBuild build) {
        Loggers.SERVER.debug("OTEL_PLUGIN: Build finished method triggered for " + getBuildId(build));

        BuildStatistics buildStatistics = build.getBuildStatistics(
                BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);
        var otelHelper = otelHelperFactory.getOTELHelper(build);

        if(otelHelper.getSpan(getBuildId(build)) != null) {
            Span span = otelHelper.getSpan(getBuildId(build));
            Loggers.SERVER.debug("OTEL_PLUGIN: Build finished and span found for " + getBuildName(build));
            try (Scope ignored = span.makeCurrent()){
                createQueuedEventsSpans(build, span);
                createBuildStepSpans(build, span);
                setArtifactAttributes(build, span);

                otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_SUCCESS_STATUS, build.getBuildStatus().isSuccessful());
                otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_FAILED_TEST_COUNT, buildStatistics.getFailedTestCount());
                otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_PROBLEMS_COUNT, buildStatistics.getCompilationErrorsCount());
                if (this.checkoutTimeMap.containsKey(span.getSpanContext().getSpanId())) {
                    otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_CHECKOUT_TIME, this.checkoutTimeMap.get(span.getSpanContext().getSpanId()));
                    this.checkoutTimeMap.remove(span.getSpanContext().getSpanId());
                }
                span.addEvent(PluginConstants.EVENT_FINISHED);
                Loggers.SERVER.debug("OTEL_PLUGIN: " + PluginConstants.EVENT_FINISHED + " event added to span for build " + getBuildName(build));
            } catch (Exception e) {
                Loggers.SERVER.error("OTEL_PLUGIN: Exception in Build Finish caused by: " + e + e.getCause() +
                        ", with message: " + e.getMessage() +
                        ", and stacktrace: " + Arrays.toString(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_FINISH + ": " + e.getMessage());
            } finally {
                span.end();
                var buildId = getBuildId(build);
                otelHelper.removeSpan(buildId);
                if (buildId.equals(getParentBuild(build)))
                    otelHelperFactory.release(build.getBuildId());
            }
        } else {
            Loggers.SERVER.warn("OTEL_PLUGIN: Build end triggered but span not found for build " + getBuildName(build));
        }
    }

    private void createQueuedEventsSpans(SRunningBuild build, Span buildSpan) {
        long startDateTime = build.getQueuedDate().getTime();
        Map<String, BigDecimal> reportedStatics = build.getStatisticValues();
        Loggers.SERVER.info("OTEL_PLUGIN: Retrieving queued event spans for build " + getBuildName(build));

        for (Map.Entry<String,BigDecimal> entry : reportedStatics.entrySet()) {
            String key = entry.getKey();
            Loggers.SERVER.debug("OTEL_PLUGIN: Queue item: " + key);
            if (key.contains("queueWaitReason:")) {
                BigDecimal value = entry.getValue();
                Loggers.SERVER.debug("OTEL_PLUGIN: Queue value: " + value);
                var otelHelper = otelHelperFactory.getOTELHelper(build);
                Span childSpan = otelHelper.createTransientSpan(key, buildSpan, startDateTime);
                List<String> keySplitList = Pattern.compile(":")
                        .splitAsStream(key)
                        .collect(Collectors.toList());
                setSpanBuildAttributes(otelHelper, build, childSpan, keySplitList.get(1), keySplitList.get(0));
                childSpan.end(startDateTime + value.longValue(), TimeUnit.MILLISECONDS);
                Loggers.SERVER.debug("OTEL_PLUGIN: Queued span added");
                startDateTime+= value.longValue();
            }
        }
    }

    private void createBuildStepSpans(SRunningBuild build, Span buildSpan) {
        Map<String, Span> blockMessageSpanMap = new HashMap<>();
        Loggers.SERVER.info("OTEL_PLUGIN: Retrieving build step event spans for build " + getBuildName(build));
        List<LogMessage> buildBlockLogs = getBuildBlockLogs(build);
        for (LogMessage logMessage: buildBlockLogs) {
            BlockLogMessage blockLogMessage = (BlockLogMessage) logMessage;
            createBlockMessageSpan(blockLogMessage, buildSpan, blockMessageSpanMap, build);
        }
    }

    private void createBlockMessageSpan(BlockLogMessage blockLogMessage, Span buildSpan, Map<String, Span> blockMessageSpanMap, SRunningBuild build) {
        Date blockMessageFinishDate = blockLogMessage.getFinishDate();
        String blockMessageStepName = blockLogMessage.getText() + " " + blockMessageFinishDate;
        Loggers.SERVER.debug("OTEL_PLUGIN: Build Step " + blockMessageStepName);
        if (blockMessageFinishDate != null) { // This filters out creating duplicate spans for Builds from their build blockMessages
            BlockLogMessage parentBlockMessage = blockLogMessage.getParent();
            Span parentSpan;
            if (parentBlockMessage != null) {
                if (parentBlockMessage.getBlockType().equals(DefaultMessagesInfo.BLOCK_TYPE_BUILD)) {
                    parentSpan = buildSpan;
                } else {
                    parentSpan = blockMessageSpanMap.get(parentBlockMessage.getText() + " " + parentBlockMessage.getFinishDate());
                }
            } else {
                parentSpan = buildSpan;
            }
            var otelHelper = otelHelperFactory.getOTELHelper(build);
            Span childSpan = otelHelper.createTransientSpan(blockMessageStepName, parentSpan, blockLogMessage.getTimestamp().getTime());
            otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_BUILD_STEP_STATUS, blockLogMessage.getStatus());
            blockMessageSpanMap.put(blockMessageStepName, childSpan);
            Loggers.SERVER.debug("OTEL_PLUGIN: Build step span added for " + blockMessageStepName);
            String spanName;
            if (blockLogMessage.getBlockDescription() != null) {
                // Only the Build Step Types "teamcity-build-step-type" has blockDescriptions
                spanName = blockLogMessage.getText() + ": " + blockLogMessage.getBlockDescription();
            } else {
                spanName = blockLogMessage.getText();
            }
            if (blockLogMessage.getBlockType().equals("checkout")) {
                calculateBuildCheckoutTime(blockLogMessage, buildSpan);
            }
            setSpanBuildAttributes(otelHelper, build, childSpan, spanName, blockLogMessage.getBlockType());
            childSpan.end(blockMessageFinishDate.getTime(),TimeUnit.MILLISECONDS);
        }
    }

    private void calculateBuildCheckoutTime(BlockLogMessage blockLogMessage, Span span) {
        if (blockLogMessage.getBlockDescription() != null && blockLogMessage.getBlockDescription().contains("checkout")) {
            Date checkoutStartDate = blockLogMessage.getTimestamp();
            Date checkoutEndDate = blockLogMessage.getFinishDate();
            if (checkoutEndDate != null) {
                Duration checkoutDuration = Duration.between(checkoutStartDate.toInstant(), checkoutEndDate.toInstant());
                long checkoutDifference = Math.abs(checkoutDuration.toMillis());
                this.checkoutTimeMap.put(span.getSpanContext().getSpanId(), checkoutDifference);
            }
        }
    }

    private List<LogMessage> getBuildBlockLogs(SRunningBuild build) {
        List<LogMessage> buildLogs = build.getBuildLog().getFilteredMessages(new LogMessageFilter() {
            @Override
            public boolean acceptMessage(LogMessage message, boolean lastMessageInParent) {
                return message instanceof BlockLogMessage;
            }
        });
        buildLogs.removeIf(logMessage -> !(logMessage instanceof BlockLogMessage));
        return buildLogs;
    }

    private void setArtifactAttributes(SRunningBuild build, Span span) {
        Loggers.SERVER.debug("OTEL_PLUGIN: Retrieving build artifact attributes for build: " + getBuildName(build) + " with id: " + getBuildId(build));
        AtomicLong buildTotalArtifactSize = new AtomicLong();
        BuildArtifacts buildArtifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
        buildArtifacts.iterateArtifacts(artifact -> {
            Loggers.SERVER.debug("OTEL_PLUGIN: Build artifact size attribute " + artifact.getName() + "=" + artifact.getSize());
            buildTotalArtifactSize.getAndAdd(artifact.getSize());
            return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
        });
        Loggers.SERVER.debug("OTEL_PLUGIN: Build total artifact size attribute " + PluginConstants.ATTRIBUTE_TOTAL_ARTIFACT_SIZE + "=" + buildTotalArtifactSize);
        span.setAttribute(PluginConstants.ATTRIBUTE_TOTAL_ARTIFACT_SIZE, String.valueOf(buildTotalArtifactSize));
    }
}
