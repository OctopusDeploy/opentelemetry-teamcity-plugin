package com.octopus.buildeventsplugin.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TeamCityBuildListener extends BuildServerAdapter {

    private final OpenTelemetry openTelemetry;
    // TODO: Change these 3 below values to System properties
    private static final String HONEYCOMB_APIKEY="XXXX";
    private static final String HONEYCOMB_DATASET="teamcity-plugin-test";
    private static final String TEAMCITY_SERVICE_NAME="TeamCity";
//    private static final String HONEYCOMB_APIKEY=System.getProperty("buildevents.plugin.apikey");
//    private static final String HONEYCOMB_DATASET=System.getProperty("buildevents.plugin.dataset");
//    private static final String HONEYCOMB_SERVICE_NAME=System.getProperty("buildevents.plugin.serviceName");
    private HashMap<String, Span> spanMap;

    public TeamCityBuildListener(EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher) {
        buildServerListenerEventDispatcher.addListener(this);

        SpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("https://api.honeycomb.io:443")
                .addHeader("x-honeycomb-team",HONEYCOMB_APIKEY)
                .addHeader("x-honeycomb-dataset",HONEYCOMB_DATASET)
                .build();
        SpanProcessor spanProcessor = BatchSpanProcessor.builder(spanExporter).build();
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(spanProcessor)
                .build();
        this.openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).buildAndRegisterGlobal();
        this.spanMap = new HashMap<>();
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build) {
        super.buildStarted(build);
        String buildNumber = build.getBuildNumber();
        String buildTypeId = build.getBuildTypeId();

        Tracer tracer = this.openTelemetry.getTracer("octopus.buildevents");

        // Returns the Parent Span or returns its own span if there is no parent
        Span parentSpan = getParentSpan(build, tracer);

        Span span=null;
        try (Scope scope = parentSpan.makeCurrent()) {
            if (this.spanMap.containsKey(buildTypeId)) {
                span =  this.spanMap.get(buildTypeId);
            } else {
                span = tracer.spanBuilder(buildTypeId)
                        .setParent(Context.current().with(parentSpan))
                        .startSpan();
                this.spanMap.put(buildTypeId, span);
            }
            span.setAttribute("octopus.buildevents.project_id", Objects.requireNonNull(build.getProjectId()));
            span.setAttribute("octopus.buildevents.agent_name", build.getAgentName());
            span.setAttribute("octopus.buildevents.commit", build.getVcsRootEntries().toString());
            span.setAttribute("service.name", TEAMCITY_SERVICE_NAME);
            span.setAttribute("service_name", build.getBuildTypeExternalId());
            span.setAttribute("name", build.getBuildTypeName());
            if (build.getBranch() != null) {
                span.setAttribute("octopus.buildevents.branch", build.getBranch().getName());
            }
            span.addEvent("Build started");
            this.spanMap.put(buildTypeId, span);
        } catch (Exception e) {
            if (span != null) {
                span.setStatus(StatusCode.ERROR, "Error during build start process");
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

        if(this.spanMap.containsKey(buildTypeId)) {
            Span span = this.spanMap.get(buildTypeId);
            try (Scope scope = span.makeCurrent()){
                span.setAttribute("octopus.buildevents.success_status", build.getBuildStatus().isSuccessful());
                span.setAttribute("octopus.buildevents.failed_test_count", buildStatistics.getFailedTestCount());
                span.setAttribute("octopus.buildevents.build_problems_count", buildStatistics.getCompilationErrorsCount());

                span.addEvent("Build finished");
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, "Error during build finish process");
            } finally {
                span.end();
                this.spanMap.remove(buildTypeId);
            }
        }
    }
}
