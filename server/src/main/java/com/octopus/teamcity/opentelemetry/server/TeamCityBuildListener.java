package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelper;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelperFactory;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.buildLog.BlockLogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessageFilter;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.log4j.Logger;
import org.apache.logging.log4j.CloseableThreadContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class TeamCityBuildListener extends BuildServerAdapter {

    public static final String BUILD_SERVICE_NAME = "teamcity-build";
    static Logger LOG = Logger.getLogger(TeamCityBuildListener.class.getName());
    private final ConcurrentHashMap<String, Long> checkoutTimeMap;
    private final OTELHelperFactory otelHelperFactory;
    private final BuildStorageManager buildStorageManager;
    private final TeamCityNodes nodesService;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public TeamCityBuildListener(
        @NotNull EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher,
        @NotNull OTELHelperFactory otelHelperFactory,
        @NotNull BuildStorageManager buildStorageManager,
        @NotNull TeamCityNodes nodesService)
    {
        this.otelHelperFactory = otelHelperFactory;
        this.buildStorageManager = buildStorageManager;
        this.nodesService = nodesService;
        this.checkoutTimeMap = new ConcurrentHashMap<>();
        buildServerListenerEventDispatcher.addListener(this);
        LOG.info("BuildListener registered.");
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        if (!nodesService.getCurrentNode().isMainNode()) return;

        try {
            var rootBuildInChain = getRootBuildInChain(build);
            try (var ignored1 = CloseableThreadContext.put("teamcity.build.id", String.valueOf(build.getBuildId()))) {
                LOG.debug(String.format("Build started method triggered for '%s', id %d", getBuildName(build), build.getBuildId()));

                try (var ignored2 = CloseableThreadContext.put("teamcity.root.build.id", String.valueOf(rootBuildInChain.getId()))) {

                    var otelHelper = otelHelperFactory.getOTELHelper(rootBuildInChain);
                    if (otelHelper.isReady()) {
                        var rootBuildInChainId = rootBuildInChain.getId();
                        LOG.debug(String.format("Root build of build id %d is %d", build.getBuildId(), rootBuildInChainId));

                        Span rootSpan = otelHelper.getOrCreateParentSpan(String.valueOf(rootBuildInChainId));
                        buildStorageManager.saveTraceId(build, rootSpan.getSpanContext().getTraceId());

                        var span = ensureSpansExistLinkingToRoot(otelHelper, build.getBuildPromotion(), rootBuildInChain);

                        try (Scope ignored3 = rootSpan.makeCurrent()) {
                            setSpanBuildAttributes(otelHelper, build, span, getBuildName(build), BUILD_SERVICE_NAME);
                            span.addEvent(PluginConstants.EVENT_STARTED);
                            LOG.debug(String.format("%s event added to span for build '%s', id %d", PluginConstants.EVENT_STARTED, getBuildName(build), build.getBuildId()));
                        } catch (Exception e) {
                            LOG.error("Exception in Build Start caused by: " + e + e.getCause() +
                                    ", with message: " + e.getMessage() +
                                    ", and stacktrace: " + Arrays.toString(e.getStackTrace()));
                            if (span != null) {
                                span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_START + ": " + e.getMessage());
                            }
                        }
                    } else {
                        LOG.info(String.format("Build start triggered for '%s', id %d and plugin not ready. This build will not be traced.", getBuildName(build), build.getBuildId()));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Exception in buildStarted caused by: " + e.getMessage(), e);
        }
    }

    private Span ensureSpansExistLinkingToRoot(OTELHelper otelHelper, BuildPromotion buildPromotion, BuildPromotion rootBuildInChain) {
        var parents = buildPromotion.getDependedOnMe();
        for (var parent : parents) {
            LOG.debug(String.format("Parents of build %d includes %d", buildPromotion.getId(), parent.getDependent().getId()));
        }

        if ((long) parents.size() == 0) {
            LOG.debug(String.format("Build %d has no parent, meaning we it's the root; creating span if needed", buildPromotion.getId()));
            return otelHelper.getOrCreateParentSpan(String.valueOf(rootBuildInChain.getId()));
        }

        //get the last one
        var immediateParentBuild = parents.stream()
                .reduce((first, second) -> second)
                .orElseThrow()
                .getDependent();
        LOG.debug(String.format("Parent of build %d is %d", buildPromotion.getId(), immediateParentBuild.getId()));
        var parentSpan = ensureSpansExistLinkingToRoot(otelHelper, immediateParentBuild, rootBuildInChain);
        var parentSpanName = String.valueOf(immediateParentBuild.getId());
        LOG.debug(String.format("Creating span for build %d, with parent id %d", buildPromotion.getId(), immediateParentBuild.getId()));
        return otelHelper.createSpan(String.valueOf(buildPromotion.getId()), parentSpan, parentSpanName);
    }

    private String getBuildId(SRunningBuild build) {
        return String.valueOf(build.getBuildId());
    }

    private String getBuildName(SRunningBuild build) {
        return build.getBuildType() != null ? build.getBuildType().getName() : "unknown_build_name";
    }

    private BuildPromotion getRootBuildInChain(SRunningBuild build) {
        BuildPromotion[] parentBuilds = build.getBuildPromotion().findTops();
        return parentBuilds[0];
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
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_ID, build.getBuildId());
        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_IS_COMPOSITE, build.getBuildPromotion().isCompositeBuild());
    }

    @Override
    public void buildFinished(@NotNull SRunningBuild build) {
        try {
            try (var ignored1 = CloseableThreadContext.put("teamcity.build.id", String.valueOf(build.getBuildId()))) {
                LOG.debug(String.format("Build finished method triggered for %s", getBuildId(build)));
                super.buildFinished(build);
                buildFinishedOrInterrupted(build);
            }
        } catch (Exception e) {
            LOG.error("Exception in buildFinished caused by: " + e.getMessage(), e);
        }
    }

    @Override
    public void buildInterrupted(@NotNull SRunningBuild build) {
        try {
            try (var ignored1 = CloseableThreadContext.put("teamcity.build.id", String.valueOf(build.getBuildId()))) {
                LOG.debug(String.format("Build interrupted method triggered for %s", getBuildId(build)));
                super.buildInterrupted(build);
                buildFinishedOrInterrupted(build);
            }
        } catch (Exception e) {
            LOG.error("Exception in buildInterrupted caused by: " + e.getMessage(), e);
        }
    }

    private void buildFinishedOrInterrupted (SRunningBuild build) {
        if (!nodesService.getCurrentNode().isMainNode()) return;

        BuildStatistics buildStatistics = build.getBuildStatistics(BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);

        var rootBuildInChain = getRootBuildInChain(build);

        try (var ignored2 = CloseableThreadContext.put("teamcity.root.build.id", String.valueOf(rootBuildInChain.getId()))) {
            var otelHelper = otelHelperFactory.getOTELHelper(rootBuildInChain);
            if (otelHelper.isReady()) {
                var span = otelHelper.getSpan(getBuildId(build));
                if (span != null) {
                    LOG.debug("Build finished and span found for '" + getBuildName(build) + "'");
                    try (Scope ignored3 = span.makeCurrent()) {
                        createQueuedEventsSpans(build, span);
                        createBuildStepSpans(build, span);
                        createTestExecutionSpans(build, span, getBuildName(build));
                        setArtifactAttributes(build, span);

                        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_SUCCESS_STATUS, build.getBuildStatus().isSuccessful());
                        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_FAILED_TEST_COUNT, buildStatistics.getFailedTestCount());
                        otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_PROBLEMS_COUNT, buildStatistics.getCompilationErrorsCount());
                        if (this.checkoutTimeMap.containsKey(span.getSpanContext().getSpanId())) {
                            otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_CHECKOUT_TIME, this.checkoutTimeMap.get(span.getSpanContext().getSpanId()));
                            this.checkoutTimeMap.remove(span.getSpanContext().getSpanId());
                        }
                        span.addEvent(PluginConstants.EVENT_FINISHED);
                        LOG.debug(PluginConstants.EVENT_FINISHED + " event added to span for build '" + getBuildName(build) + "' id " + build.getBuildId());
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
                    LOG.warn("Build end triggered but span not found for build '" + getBuildName(build) + "' id " + build.getBuildId());
                }
            } else {
                LOG.warn(String.format("Build finished (or interrupted) for '%s', id %d and plugin not ready.", getBuildName(build), build.getBuildId()));
            }
        }
    }

    private void createTestExecutionSpans(SRunningBuild build, Span parentSpan, String parentSpanName) {
        if (build.isCompositeBuild()) return;

        var buildStatistics = build.getBuildStatistics(
                BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);

        var tests = buildStatistics.getAllTests();
        var otelHelper = otelHelperFactory.getOTELHelper(getRootBuildInChain(build));

        if (!tests.isEmpty()) {
            var startTime = build.convertToServerTime(Objects.requireNonNull(build.getClientStartDate())).getTime(); // epoch milliseconds
            var spanName = "Tests";
            LOG.info("Creating child span '" + spanName + "' under parent " + parentSpanName);

            var testsSpan = otelHelper.createTransientSpan(spanName, parentSpan, startTime);
            setSpanBuildAttributes(otelHelper, build, testsSpan, spanName, "tests-execution");

            try {
                LOG.info("Creating " + tests.size() + " test spans under '" + parentSpanName + "' > '" + spanName + "'");
                for (var test : tests) {
                    createTestExecutionSpan(otelHelper, build, test, testsSpan, startTime);
                }
                LOG.info("Created " + tests.size() + " test spans under '" + parentSpanName + "' > '" + spanName + "'");
            } finally {
                var finishDate = build.getFinishDate();
                if (finishDate != null) {
                    var endTime = Objects.requireNonNull(finishDate).toInstant();
                    testsSpan.end(endTime);
                } else {
                    testsSpan.end();
                }
            }
        }

    }

    private void createTestExecutionSpan(OTELHelper otelHelper, SRunningBuild build, STestRun test, Span parentSpan, long startTime) {
        var durationMs = test.getDuration(); // milliseconds
        // For now, we are starting all tests in sync with their parent build. This isn't ideal, however the SDK doesn't expose test start/finish times here.
        var endTime = startTime + durationMs;
        var failed = test.getStatus().isFailed();
        var passed = test.getStatus() == Status.NORMAL;
        var muted = test.isMuted();
        var ignored = test.isIgnored();
        var testName = test.getTest().getName().getAsString();

        var humanReadableStatus = "unknown";

        if (muted && failed) {
            humanReadableStatus = "muted failure";
        }
        else if (failed) {
            humanReadableStatus = "failed";
        }
        else if (passed) {
            humanReadableStatus = "passed";
        }
        else if (ignored) {
            humanReadableStatus = "ignored";
        }

        Span childSpan = otelHelper.createTransientSpan(testName, parentSpan, startTime);
        otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_TEST_STATUS, humanReadableStatus);
        otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_TEST_PASSED_FLAG, passed);
        otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_TEST_FAILED_FLAG, failed);
        otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_TEST_IGNORED_FLAG, ignored);
        otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_TEST_MUTED_FLAG, muted);
        setSpanBuildAttributes(otelHelper, build, childSpan, testName, "test-execution");

        if (failed) {
            childSpan.setStatus(StatusCode.ERROR);
        }
        childSpan.end(endTime, TimeUnit.MILLISECONDS);
    }

    private void createQueuedEventsSpans(SRunningBuild build, Span buildSpan) {
        long startDateTime = build.getQueuedDate().getTime();
        Map<String, BigDecimal> reportedStatics = build.getStatisticValues();

        for (Map.Entry<String,BigDecimal> entry : reportedStatics.entrySet()) {
            String key = entry.getKey();
            if (key.contains("queueWaitReason:")) {
                BigDecimal value = entry.getValue();
                var otelHelper = otelHelperFactory.getOTELHelper(getRootBuildInChain(build));
                Span childSpan = otelHelper.createTransientSpan(key, buildSpan, startDateTime);
                List<String> keySplitList = Pattern.compile(":")
                        .splitAsStream(key)
                        .toList();
                setSpanBuildAttributes(otelHelper, build, childSpan, keySplitList.get(1), keySplitList.get(0));
                childSpan.end(startDateTime + value.longValue(), TimeUnit.MILLISECONDS);
                startDateTime+= value.longValue();
            }
        }
    }

    private void createBuildStepSpans(SRunningBuild build, Span buildSpan) {
        if (build.isCompositeBuild()) return;
        Map<String, Span> blockMessageSpanMap = new HashMap<>();
        List<LogMessage> buildBlockLogs = getBuildBlockLogs(build);
        for (LogMessage logMessage: buildBlockLogs) {
            BlockLogMessage blockLogMessage = (BlockLogMessage) logMessage;
            createBlockMessageSpan(blockLogMessage, buildSpan, blockMessageSpanMap, build);
        }
    }

    private void createBlockMessageSpan(BlockLogMessage blockLogMessage, Span buildSpan, Map<String, Span> blockMessageSpanMap, SRunningBuild build) {
        if (blockLogMessage.getBlockType().equals("$TEST_BLOCK$")) {
            //we handle these explicitly when we publish tests (in createTestExecutionSpans)
            return;
        }

        Date blockMessageFinishDate = blockLogMessage.getFinishDate();
        String blockMessageStepName = blockLogMessage.getText() + " " + blockMessageFinishDate;
        if (blockMessageFinishDate != null) { // This filters out creating duplicate spans for Builds from their build blockMessages
            BlockLogMessage parentBlockMessage = blockLogMessage.getParent();
            Span parentSpan;
            if (parentBlockMessage != null) {
                if (parentBlockMessage.getBlockType().equals(DefaultMessagesInfo.BLOCK_TYPE_BUILD)) {
                    parentSpan = buildSpan;
                } else {
                    var parentBlockMessageKey = parentBlockMessage.getText() + " " + parentBlockMessage.getFinishDate();
                    parentSpan = blockMessageSpanMap.get(parentBlockMessageKey);
                    if (parentSpan == null) {
                        LOG.warn("Attempted to find span in map using key '" + parentBlockMessageKey + "', but it was not found; using buildspan instead");
                        parentSpan = buildSpan;
                    }
                }
            } else {
                parentSpan = buildSpan;
            }
            if (parentSpan == null) {
                LOG.error("Parent span is null; not creating block message spans for '" + blockMessageStepName + "'");
                return;
            }
            var otelHelper = otelHelperFactory.getOTELHelper(getRootBuildInChain(build));
            Span childSpan = otelHelper.createTransientSpan(blockMessageStepName, parentSpan, blockLogMessage.getTimestamp().getTime());
            otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_BUILD_STEP_STATUS, blockLogMessage.getStatus());
            blockMessageSpanMap.put(blockMessageStepName, childSpan);
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
            public boolean acceptMessage(@NotNull LogMessage message, boolean lastMessageInParent) {
                return message instanceof BlockLogMessage;
            }
        });
        buildLogs.removeIf(logMessage -> !(logMessage instanceof BlockLogMessage));
        return buildLogs;
    }

    private void setArtifactAttributes(SRunningBuild build, Span span) {
        if (build.isCompositeBuild()) return;
        LOG.debug("Retrieving build artifact attributes for build '" + getBuildName(build) + "' with id: " + getBuildId(build));
        AtomicLong buildTotalArtifactSize = new AtomicLong();
        BuildArtifacts buildArtifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_DEFAULT);
        buildArtifacts.iterateArtifacts(artifact -> {
            buildTotalArtifactSize.getAndAdd(artifact.getSize());
            return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
        });
        LOG.debug("Build total artifact size attribute " + PluginConstants.ATTRIBUTE_TOTAL_ARTIFACT_SIZE + "=" + buildTotalArtifactSize);
        span.setAttribute(PluginConstants.ATTRIBUTE_TOTAL_ARTIFACT_SIZE, String.valueOf(buildTotalArtifactSize));
    }
}
