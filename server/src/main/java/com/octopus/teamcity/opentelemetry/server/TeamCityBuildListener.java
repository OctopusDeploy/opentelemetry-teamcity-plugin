package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelper;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelperFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.buildLog.BlockLogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessageFilter;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TeamCityBuildListener extends BuildServerAdapter {

    public static final String BUILD_SERVICE_NAME = "teamcity-build";
    static Logger LOG = Logger.getLogger(TeamCityBuildListener.class.getName());
    private final ConcurrentHashMap<String, Long> checkoutTimeMap;
    private OTELHelperFactory otelHelperFactory;
    private BuildStorageManager buildStorageManager;
    private final TeamCityNodes nodesService;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public TeamCityBuildListener(
        EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher,
        OTELHelperFactory otelHelperFactory,
        BuildStorageManager buildStorageManager,
        TeamCityNodes nodesService
    ) {
        this.otelHelperFactory = otelHelperFactory;
        this.buildStorageManager = buildStorageManager;
        this.nodesService = nodesService;
        this.checkoutTimeMap = new ConcurrentHashMap<>();
        buildServerListenerEventDispatcher.addListener(this);
        LOG.info("BuildListener registered.");
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        LOG.debug(String.format("Build started method triggered for %s, id %d", getBuildName(build), build.getBuildId()));

        if (!nodesService.getCurrentNode().isMainNode())
        {
            LOG.debug(String.format("This is not the main node - skipping OTEL tracing for %s (id %d) and leaving that responsibility for the main node to deal with", getBuildName(build), build.getBuildId()));
            return;
        }

        var parentBuild = getRootBuildInChain(build);
        var otelHelper = otelHelperFactory.getOTELHelper(parentBuild);
        if (otelHelper.isReady()) {
            var parentBuildId = parentBuild.getId();
            LOG.debug(String.format("Root build of build id %d is %d", build.getBuildId(), parentBuildId));

            Span parentSpan = otelHelper.getParentSpan(String.valueOf(parentBuildId));
            Span span = otelHelper.createSpan(getBuildId(build), parentSpan);
            LOG.debug(String.format("Span created for %s, id %d", getBuildName(build), build.getBuildId()));

            buildStorageManager.saveTraceId(build, span.getSpanContext().getTraceId());

            try (Scope ignored = parentSpan.makeCurrent()) {
                setSpanBuildAttributes(otelHelper, build, span, getBuildName(build), BUILD_SERVICE_NAME);
                span.addEvent(PluginConstants.EVENT_STARTED);
                LOG.debug(String.format("%s event added to span for build %s, id %d", PluginConstants.EVENT_STARTED, getBuildName(build), build.getBuildId()));
            } catch (Exception e) {
                LOG.error("Exception in Build Start caused by: " + e + e.getCause() +
                        ", with message: " + e.getMessage() +
                        ", and stacktrace: " + Arrays.toString(e.getStackTrace()));
                if (span != null) {
                    span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_START + ": " + e.getMessage());
                }
            }
        } else {
            LOG.info(String.format("Build start triggered for %s, id %d and plugin not ready. This build will not be traced.", getBuildName(build), build.getBuildId()));
        }
    }

    private String getBuildId(SRunningBuild build) {
        return String.valueOf(build.getBuildId());
    }

    private String getBuildName(SRunningBuild build) {
        return build.getBuildType() != null ? build.getBuildType().getName() : "unknown_build_name";
    }

    private BuildPromotion getRootBuildInChain(SRunningBuild build) {
        BuildPromotion[] parentBuilds = build.getBuildPromotion().findTops();
        BuildPromotion parentBuildPromotion = parentBuilds[0];
        return parentBuildPromotion;
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
        LOG.debug(String.format("Build finished method triggered for %s", getBuildId(build)));
        super.buildFinished(build);
        buildFinishedOrInterrupted(build);
    }

    @Override
    public void buildInterrupted(@NotNull SRunningBuild build) {
        LOG.debug(String.format("Build interrupted method triggered for %s", getBuildId(build)));
        super.buildInterrupted(build);
        buildFinishedOrInterrupted(build);
    }

    private void buildFinishedOrInterrupted (SRunningBuild build) {
        if (!nodesService.getCurrentNode().isMainNode()) return;

        BuildStatistics buildStatistics = build.getBuildStatistics(
                BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);
        var parentBuild = getRootBuildInChain(build);
        var otelHelper = otelHelperFactory.getOTELHelper(parentBuild);
        if (otelHelper.isReady()) {
            if (otelHelper.getSpan(getBuildId(build)) != null) {
                Span span = otelHelper.getSpan(getBuildId(build));
                LOG.debug("Build finished and span found for " + getBuildName(build));
                try (Scope ignored = span.makeCurrent()) {
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
                    LOG.debug("" + PluginConstants.EVENT_FINISHED + " event added to span for build " + getBuildName(build));
                } catch (Exception e) {
                    LOG.error("Exception in Build Finish caused by: " + e + e.getCause() +
                            ", with message: " + e.getMessage() +
                            ", and stacktrace: " + Arrays.toString(e.getStackTrace()));
                    span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_FINISH + ": " + e.getMessage());
                } finally {
                    span.end();
                    var buildId = getBuildId(build);
                    otelHelper.removeSpan(buildId);
                    if (buildId.equals(String.valueOf(getRootBuildInChain(build).getId())))
                        otelHelperFactory.release(build.getBuildId());
                }
            } else {
                LOG.warn("Build end triggered but span not found for build " + getBuildName(build));
            }
        } else {
            LOG.warn(String.format("Build finished (or interrupted) for %s, id %d and plugin not ready.", getBuildName(build), build.getBuildId()));
        }
    }

    private void createQueuedEventsSpans(SRunningBuild build, Span buildSpan) {
        long startDateTime = build.getQueuedDate().getTime();
        Map<String, BigDecimal> reportedStatics = build.getStatisticValues();
        LOG.info("Retrieving queued event spans for build " + getBuildName(build));

        for (Map.Entry<String,BigDecimal> entry : reportedStatics.entrySet()) {
            String key = entry.getKey();
            LOG.debug("Queue item: " + key);
            if (key.contains("queueWaitReason:")) {
                BigDecimal value = entry.getValue();
                LOG.debug("Queue value: " + value);
                var otelHelper = otelHelperFactory.getOTELHelper(getRootBuildInChain(build));
                Span childSpan = otelHelper.createTransientSpan(key, buildSpan, startDateTime);
                List<String> keySplitList = Pattern.compile(":")
                        .splitAsStream(key)
                        .collect(Collectors.toList());
                setSpanBuildAttributes(otelHelper, build, childSpan, keySplitList.get(1), keySplitList.get(0));
                childSpan.end(startDateTime + value.longValue(), TimeUnit.MILLISECONDS);
                LOG.debug("Queued span added");
                startDateTime+= value.longValue();
            }
        }
    }

    private void createBuildStepSpans(SRunningBuild build, Span buildSpan) {
        Map<String, Span> blockMessageSpanMap = new HashMap<>();
        LOG.info("Retrieving build step event spans for build " + getBuildName(build));
        List<LogMessage> buildBlockLogs = getBuildBlockLogs(build);
        for (LogMessage logMessage: buildBlockLogs) {
            BlockLogMessage blockLogMessage = (BlockLogMessage) logMessage;
            createBlockMessageSpan(blockLogMessage, buildSpan, blockMessageSpanMap, build);
        }
    }

    private void createBlockMessageSpan(BlockLogMessage blockLogMessage, Span buildSpan, Map<String, Span> blockMessageSpanMap, SRunningBuild build) {
        Date blockMessageFinishDate = blockLogMessage.getFinishDate();
        String blockMessageStepName = blockLogMessage.getText() + " " + blockMessageFinishDate;
        LOG.debug("Build Step " + blockMessageStepName);
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
            var otelHelper = otelHelperFactory.getOTELHelper(getRootBuildInChain(build));
            Span childSpan = otelHelper.createTransientSpan(blockMessageStepName, parentSpan, blockLogMessage.getTimestamp().getTime());
            otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_BUILD_STEP_STATUS, blockLogMessage.getStatus());
            blockMessageSpanMap.put(blockMessageStepName, childSpan);
            LOG.debug("Build step span added for " + blockMessageStepName);
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
        LOG.debug("Retrieving build artifact attributes for build: " + getBuildName(build) + " with id: " + getBuildId(build));
        AtomicLong buildTotalArtifactSize = new AtomicLong();
        BuildArtifacts buildArtifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
        buildArtifacts.iterateArtifacts(artifact -> {
            LOG.debug("Build artifact size attribute " + artifact.getName() + "=" + artifact.getSize());
            buildTotalArtifactSize.getAndAdd(artifact.getSize());
            return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
        });
        LOG.debug("Build total artifact size attribute " + PluginConstants.ATTRIBUTE_TOTAL_ARTIFACT_SIZE + "=" + buildTotalArtifactSize);
        span.setAttribute(PluginConstants.ATTRIBUTE_TOTAL_ARTIFACT_SIZE, String.valueOf(buildTotalArtifactSize));
    }
}
