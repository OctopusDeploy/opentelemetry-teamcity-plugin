package com.octopus.buildeventsplugin.server;

import com.octopus.buildeventsplugin.com.PluginConstants;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TeamCityBuildListener extends BuildServerAdapter {

    private final OpenTelemetry openTelemetry;
    private static final String ENDPOINT = TeamCityProperties.getProperty(PluginConstants.PROPERTY_KEY_ENDPOINT);
    private HashMap<String, Span> spanMap;

    public TeamCityBuildListener(EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher) {
        buildServerListenerEventDispatcher.addListener(this);

        Resource serviceNameResource = Resource
                .create(Attributes.of(ResourceAttributes.SERVICE_NAME, PluginConstants.SERVICE_NAME));
        Map<String, String> headers = getExporterHeaders();
        OtlpGrpcSpanExporterBuilder spanExporterBuilder = OtlpGrpcSpanExporter.builder();
        spanExporterBuilder.setEndpoint(ENDPOINT);
        headers.forEach(spanExporterBuilder::addHeader);
        SpanExporter spanExporter = spanExporterBuilder.build();
        SpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter).build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .addSpanProcessor(spanProcessor)
                .build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        this.spanMap = new HashMap<>();
    }

    private Map<String, String> getExporterHeaders() throws IllegalStateException {
        Properties internalProperties = TeamCityProperties.getAllProperties().first;
        for (Map.Entry<Object,Object> entry : internalProperties.entrySet()) {
            String propertyName = entry.getKey().toString();
            if (propertyName.contains(PluginConstants.PROPERTY_KEY_HEADERS)) {
                return Arrays.stream(entry.getValue().toString().split(","))
                        .map(s -> s.split(":"))
                        .collect(Collectors.toMap(
                                a -> a[0],
                                a -> a[1]
                        ));
            }
        }
        throw new IllegalStateException(PluginConstants.EXCEPTION_ERROR_MESSAGE_HEADERS_UNSET);
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        super.buildStarted(build);
        String buildTypeId = build.getBuildTypeId();

        Tracer tracer = this.openTelemetry.getTracer(PluginConstants.TRACER_INSTRUMENTATION_NAME);
        Span parentSpan = getParentSpan(build, tracer);
        Span span = this.spanMap.containsKey(buildTypeId) ?
                this.spanMap.get(buildTypeId) :
                tracer.spanBuilder(buildTypeId).setParent(Context.current().with(parentSpan)).startSpan();
        this.spanMap.put(buildTypeId, span);

        try (Scope scope = parentSpan.makeCurrent()) {
            if (build.getBuildType() != null) {
                span.setAttribute(PluginConstants.ATTRIBUTE_PROJECT_NAME, build.getBuildType().getProject().getName());
            }
            if (build.getProjectExternalId() != null) {
                span.setAttribute(PluginConstants.ATTRIBUTE_PROJECT_ID, build.getProjectExternalId());
            }
            span.setAttribute(PluginConstants.ATTRIBUTE_AGENT_NAME, build.getAgentName());
            span.setAttribute(PluginConstants.ATTRIBUTE_AGENT_TYPE, build.getAgent().getAgentTypeId());
            span.setAttribute(PluginConstants.ATTRIBUTE_BUILD_NUMBER, build.getBuildNumber());
            if (!build.getRevisions().isEmpty()) {
                span.setAttribute(PluginConstants.ATTRIBUTE_COMMIT, build.getRevisions().get(0).getRevisionDisplayName());
            }
            span.setAttribute(PluginConstants.ATTRIBUTE_SERVICE_NAME, build.getBuildTypeExternalId());
            span.setAttribute(PluginConstants.ATTRIBUTE_NAME, build.getBuildType().getName());
            if (build.getBranch() != null) {
                span.setAttribute(PluginConstants.ATTRIBUTE_BRANCH, build.getBranch().getName());
            }
            span.addEvent(PluginConstants.EVENT_STARTED);
            this.spanMap.put(buildTypeId, span);
        } catch (Exception e) {
            if (span != null) {
                span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_START + ": " + e.getMessage());
            }
        }
    }

    private Span getParentSpan(SBuild build, Tracer tracer) {
        BuildPromotion[] topParentBuild = build.getBuildPromotion().findTops();
        BuildPromotion buildPromotion = topParentBuild[0];
        if (!this.spanMap.containsKey(buildPromotion.getBuildTypeId())) {
            this.spanMap.put(buildPromotion.getBuildTypeId(), tracer.spanBuilder(buildPromotion.getBuildTypeId()).startSpan());
        }
        return this.spanMap.get(buildPromotion.getBuildTypeId());
    }

    @Override
    public void buildFinished(@NotNull SRunningBuild build) {
        super.buildFinished(build);
        buildFinishedOrInterrupted(build);
    }

    @Override
    public void buildInterrupted(@NotNull SRunningBuild build) {
        super.buildInterrupted(build);
        buildFinishedOrInterrupted(build);
    }

    private void buildFinishedOrInterrupted (SBuild build) {
        String buildTypeId = build.getBuildTypeId();
        BuildStatistics buildStatistics = build.getBuildStatistics(
                BuildStatisticsOptions.ALL_TESTS_NO_DETAILS);
        Tracer tracer = this.openTelemetry.getTracer(PluginConstants.TRACER_INSTRUMENTATION_NAME);

        if(this.spanMap.containsKey(buildTypeId)) {
            Span span = this.spanMap.get(buildTypeId);
            try (Scope scope = span.makeCurrent()){
                createQueuedEventsSpans(build, tracer, span);
                createBuildStepSpans(build, tracer, span);
                getArtifactAttributes(build,span);

                span.setAttribute(PluginConstants.ATTRIBUTE_SUCCESS_STATUS, build.getBuildStatus().isSuccessful());
                span.setAttribute(PluginConstants.ATTRIBUTE_FAILED_TEST_COUNT, buildStatistics.getFailedTestCount());
                span.setAttribute(PluginConstants.ATTRIBUTE_BUILD_PROBLEMS_COUNT, buildStatistics.getCompilationErrorsCount());

                span.addEvent(PluginConstants.EVENT_FINISHED);
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, PluginConstants.EXCEPTION_ERROR_MESSAGE_DURING_BUILD_FINISH + ": " + e.getMessage());
            } finally {
                span.end();
                this.spanMap.remove(buildTypeId);
            }
        }
    }

    private void getArtifactAttributes(SBuild build, Span span) {
        BuildArtifacts buildArtifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);
        AtomicInteger index = new AtomicInteger(0);
        buildArtifacts.iterateArtifacts(artifact -> {
            index.getAndIncrement();
            span.setAttribute(PluginConstants.ATTRIBUTE_ARTIFACT_NAME + "_" + index, artifact.getName());
            span.setAttribute(PluginConstants.ATTRIBUTE_ARTIFACT_SIZE + "_" + index, artifact.getSize());
            return BuildArtifacts.BuildArtifactsProcessor.Continuation.CONTINUE;
        });
    }

    private void createQueuedEventsSpans(SBuild build, Tracer tracer, Span span) {
        long startDateTime = build.getQueuedDate().getTime();
        Map<String, BigDecimal> reportedStatics = build.getStatisticValues();

        for (Map.Entry<String,BigDecimal> entry : reportedStatics.entrySet()) {
            String key = entry.getKey();
            if (key.contains("queueWaitReason:")) {
                BigDecimal value = entry.getValue();
                Span childSpan = createChildSpan(tracer, span, key, startDateTime);
                List<String> keySplitList = Pattern.compile(":")
                        .splitAsStream(key)
                        .collect(Collectors.toList());
                childSpan.setAttribute(PluginConstants.ATTRIBUTE_NAME, keySplitList.get(1));
                childSpan.setAttribute(PluginConstants.ATTRIBUTE_SERVICE_NAME, keySplitList.get(0));
                childSpan.end(startDateTime + value.longValue(), TimeUnit.MILLISECONDS);
                startDateTime+= value.longValue();
            }
        }
    }

    private void createBuildStepSpans(SBuild build, Tracer tracer, Span span) {
        List<LogMessage> buildStepLogs = build.getBuildLog().getFilteredMessages(new LogMessageFilter() {
            @Override
            public boolean acceptMessage(LogMessage message, boolean lastMessageInParent) {
                return message instanceof BlockLogMessage;
            }
        });
        for (LogMessage logmessage: buildStepLogs) {
            BlockLogMessage blockLogMessage = (BlockLogMessage) logmessage;
            Date finishedDate = blockLogMessage.getFinishDate();
            String buildStepName = blockLogMessage.getText();
            if (finishedDate != null) {
                Span childSpan = createChildSpan(tracer, span, blockLogMessage.getText(), blockLogMessage.getTimestamp().getTime());
                if (blockLogMessage.getBlockDescription() != null) {
                    childSpan.setAttribute(PluginConstants.ATTRIBUTE_NAME, buildStepName + ": " + blockLogMessage.getBlockDescription());
                } else {
                    childSpan.setAttribute(PluginConstants.ATTRIBUTE_NAME, buildStepName);
                }
                childSpan.setAttribute(PluginConstants.ATTRIBUTE_SERVICE_NAME, blockLogMessage.getBlockType());
                childSpan.end(finishedDate.getTime(),TimeUnit.MILLISECONDS);
            }
        }
    }

    private Span createChildSpan(Tracer tracer, Span parentSpan, String spanName, long startTime) {
        return tracer.spanBuilder(spanName)
                .setParent(Context.current().with(parentSpan))
                .setStartTimestamp(startTime,TimeUnit.MILLISECONDS)
                .startSpan();
    }
}
