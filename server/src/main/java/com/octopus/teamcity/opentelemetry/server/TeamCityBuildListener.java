package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.common.PluginConstants;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.serverSide.buildLog.BlockLogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessage;
import jetbrains.buildServer.serverSide.buildLog.LogMessageFilter;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TeamCityBuildListener extends BuildServerAdapter {

    private final OTELHelper otelHelper;
    private static final String ENDPOINT = TeamCityProperties.getProperty(PluginConstants.PROPERTY_KEY_ENDPOINT);

    public TeamCityBuildListener(EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher) {
        buildServerListenerEventDispatcher.addListener(this);
        Loggers.SERVER.info("OTEL_PLUGIN: OTEL_PLUGIN: BuildListener registered.");
        this.otelHelper = new OTELHelper(getExporterHeaders(), ENDPOINT);
    }

    private Map<String, String> getExporterHeaders() throws IllegalStateException {
        Properties internalProperties = TeamCityProperties.getAllProperties().first;
        Loggers.SERVER.debug("OTEL_PLUGIN: TeamCity internal properties: " + internalProperties);

        for (Map.Entry<Object,Object> entry : internalProperties.entrySet()) {
            String propertyName = entry.getKey().toString();
            if (propertyName.contains(PluginConstants.PROPERTY_KEY_HEADERS)) {
                Object propertyValue = entry.getValue();
                Loggers.SERVER.debug("OTEL_PLUGIN: Internal Property Name: " + propertyName);
                Loggers.SERVER.debug("OTEL_PLUGIN: Internal Property Value: " + propertyValue);

                return Arrays.stream(propertyValue.toString().split(","))
                        .map(propertyValuesSplit -> propertyValuesSplit.split(":"))
                        .collect(Collectors.toMap(key -> key[0], value -> value[1]));
            }
        }
        throw new IllegalStateException(PluginConstants.EXCEPTION_ERROR_MESSAGE_HEADERS_UNSET);
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        if (buildListenerReady()) {
            String buildId = getBuildId(build);
            String buildName = getBuildName(build);
            Loggers.SERVER.debug("OTEL_PLUGIN: Build started method triggered for " + buildName);

            String parentBuildId = getParentBuild(build);
            Span parentSpan = this.otelHelper.getParentSpan(parentBuildId);
            Span span = this.otelHelper.createSpan(buildId, parentSpan);
            this.otelHelper.addSpanToMap(buildId, span);
            Loggers.SERVER.info("OTEL_PLUGIN: Span created for " + buildName);

            try (Scope ignored = parentSpan.makeCurrent()) {
                setSpanBuildAttributes(build, buildName, span);
                span.addEvent(PluginConstants.EVENT_STARTED);
                Loggers.SERVER.info(PluginConstants.EVENT_STARTED + " event added to span for build " + buildName);
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

    private boolean buildListenerReady() {
        return (this.otelHelper != null) && this.otelHelper.pluginReady();
    }

    private String getBuildId (SRunningBuild build) {
        return String.valueOf(build.getBuildId());
    }

    private String getBuildName(SRunningBuild build) {
        return build.getBuildType() != null ? build.getBuildType().getName() : null;
    }

    private String getParentBuild(SRunningBuild build) {
        BuildPromotion[] topParentBuild = build.getBuildPromotion().findTops();
        BuildPromotion buildPromotion = topParentBuild[0];
        Loggers.SERVER.debug("OTEL_PLUGIN: Top Build Parent: " + buildPromotion);
        Loggers.SERVER.debug("OTEL_PLUGIN: Top Build Parent Id: " + buildPromotion.getId());
        return String.valueOf(buildPromotion.getId());
    }


    private void setSpanBuildAttributes(SRunningBuild build, String buildName, Span span) {
        if (build.getBuildType() != null) {
            this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_PROJECT_NAME, build.getBuildType().getProject().getName());
        }
        if (build.getBranch() != null ) {
            this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BRANCH,  build.getBranch().getName());
        }
        if (!build.getRevisions().isEmpty()) {
            this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_COMMIT,  build.getRevisions().iterator().next().getRevisionDisplayName());
        }
        if (build.getProjectExternalId() != null ) {
            this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_PROJECT_ID, build.getProjectExternalId());
        }
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_TYPE_ID, build.getBuildTypeId());
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_TYPE_EXTERNAL_ID, build.getBuildTypeExternalId());
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_AGENT_NAME, build.getAgentName());
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_AGENT_TYPE, build.getAgent().getAgentTypeId());
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_NUMBER, build.getBuildNumber());
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_SERVICE_NAME,  build.getBuildTypeExternalId());
        this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_NAME, buildName);
    }

    @Override
    public void buildFinished(@NotNull SRunningBuild build) {
        super.buildFinished(build);
        if (buildListenerReady()) {
            buildFinishedOrInterrupted(build);
        }
    }

    @Override
    public void buildInterrupted(@NotNull SRunningBuild build) {
        super.buildInterrupted(build);
        if (buildListenerReady()) {
            buildFinishedOrInterrupted(build);
        }
    }

    private void buildFinishedOrInterrupted (SRunningBuild build) {
        String buildId = getBuildId(build);
        String buildName = getBuildName(build);
        Loggers.SERVER.debug("OTEL_PLUGIN: Build finished method triggered for " + buildId);

        BuildStatistics buildStatistics = build.getBuildStatistics(
                BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);

        if(this.otelHelper.getSpanFromMap(buildId) != null) {
            Span span = this.otelHelper.getSpanFromMap(buildId);
            Loggers.SERVER.debug("OTEL_PLUGIN: Tracer initialized and span found for " + buildName);
            try (Scope ignored = span.makeCurrent()){
                createQueuedEventsSpans(build, buildName, span);
                createBuildStepSpans(build, buildName, span);
                getArtifactAttributes(build, buildName, span);

                this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_SUCCESS_STATUS, build.getBuildStatus().isSuccessful());
                this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_FAILED_TEST_COUNT, buildStatistics.getFailedTestCount());
                this.otelHelper.addAttributeToSpan(span, PluginConstants.ATTRIBUTE_BUILD_PROBLEMS_COUNT, buildStatistics.getCompilationErrorsCount());

                span.addEvent(PluginConstants.EVENT_FINISHED);
                Loggers.SERVER.debug(PluginConstants.EVENT_FINISHED + " event added to span for build " + buildName);
            } catch (Exception e) {
                Loggers.SERVER.error("OTEL_PLUGIN: Exception in Build Finish caused by: " + e + e.getCause() +
                        ", with message: " + e.getMessage() +
                        ", and stacktrace: " + Arrays.toString(e.getStackTrace()));
                span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_FINISH + ": " + e.getMessage());
            } finally {
                span.end();
                this.otelHelper.removeSpanFromMap(buildId);
            }
        } else {
            Loggers.SERVER.warn("OTEL_PLUGIN: Build end triggered but span not found for build " + buildName);
        }
    }

    private void createQueuedEventsSpans(SRunningBuild build, String buildName, Span parentSpan) {
        long startDateTime = build.getQueuedDate().getTime();
        Map<String, BigDecimal> reportedStatics = build.getStatisticValues();
        Loggers.SERVER.info("OTEL_PLUGIN: Retrieving queued event spans for build " + buildName);

        for (Map.Entry<String,BigDecimal> entry : reportedStatics.entrySet()) {
            String key = entry.getKey();
            Loggers.SERVER.debug("OTEL_PLUGIN: Queue item: " + key);
            if (key.contains("queueWaitReason:")) {
                BigDecimal value = entry.getValue();
                Loggers.SERVER.debug("OTEL_PLUGIN: Queue value: " + value);
                Span childSpan = this.otelHelper.createChildSpan(parentSpan, key, startDateTime);
                List<String> keySplitList = Pattern.compile(":")
                        .splitAsStream(key)
                        .collect(Collectors.toList());
                this.otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_NAME, keySplitList.get(1));
                this.otelHelper.addAttributeToSpan(childSpan, PluginConstants.ATTRIBUTE_SERVICE_NAME, keySplitList.get(0));
                childSpan.end(startDateTime + value.longValue(), TimeUnit.MILLISECONDS);
                Loggers.SERVER.debug("OTEL_PLUGIN: Queued span added");
                startDateTime+= value.longValue();
            }
        }
    }

    private void createBuildStepSpans(SRunningBuild build, String buildName, Span span) {
        Loggers.SERVER.info("OTEL_PLUGIN: Retrieving build step event spans for build " + buildName);
        List<LogMessage> buildBlockLogs = getBuildBlockLogs(build);
        createBlockMessageSpans(buildBlockLogs, span);
    }

    private void createBlockMessageSpans (List<LogMessage> buildBlockLogs, Span span) {
        for (LogMessage logmessage: buildBlockLogs) {
            BlockLogMessage blockLogMessage = (BlockLogMessage) logmessage;
            Date finishedDate = blockLogMessage.getFinishDate();
            String buildStepName = blockLogMessage.getText();
            Loggers.SERVER.debug("OTEL_PLUGIN: Build Step " + buildStepName + " with finish time " + finishedDate);
            if (finishedDate != null) {
                Span childSpan = this.otelHelper.createChildSpan(span, blockLogMessage.getText(), blockLogMessage.getTimestamp().getTime());
                if (blockLogMessage.getBlockDescription() != null) {
                    childSpan.setAttribute(PluginConstants.ATTRIBUTE_NAME, buildStepName + ": " + blockLogMessage.getBlockDescription());
                } else {
                    childSpan.setAttribute(PluginConstants.ATTRIBUTE_NAME, buildStepName);
                }
                childSpan.setAttribute(PluginConstants.ATTRIBUTE_SERVICE_NAME, blockLogMessage.getBlockType());
                childSpan.end(finishedDate.getTime(),TimeUnit.MILLISECONDS);
                Loggers.SERVER.debug("OTEL_PLUGIN: Build step span added for " + buildStepName);
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

    private void getArtifactAttributes(SRunningBuild build, String buildName, Span span) {
        Loggers.SERVER.info("OTEL_PLUGIN: Retrieving build artifact attributes for build " + buildName);
        BuildArtifacts buildArtifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
        buildArtifacts.iterateArtifacts(artifact -> {
            span.setAttribute(artifact.getName() + PluginConstants.ATTRIBUTE_ARTIFACT_SIZE, artifact.getSize());
            Loggers.SERVER.debug("OTEL_PLUGIN: Build artifact attribute " + artifact.getName() + "=" + artifact.getSize());
            return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
        });
    }
}
