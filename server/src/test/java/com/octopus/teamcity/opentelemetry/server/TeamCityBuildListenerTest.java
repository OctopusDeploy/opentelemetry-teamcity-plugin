package com.octopus.teamcity.opentelemetry.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.util.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamCityBuildListenerTest {

    private OTELHelper otelHelper;
    private TeamCityBuildListener buildListener;

    @BeforeEach
    void setUp(@Mock EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher) {
        final String ENDPOINT = "https://otel.endpoint.com";
        final Map<String, String> HEADERS = new HashMap<>() {{
            put("testHeader1", "testHeaderValue1");
            put("testHeader2", "testHeaderValue2");
        }};
        GlobalOpenTelemetry.resetForTest();
        this.otelHelper = new OTELHelper(HEADERS, ENDPOINT);
        this.buildListener = new TeamCityBuildListener(buildServerListenerEventDispatcher, otelHelper);
    }

    @Test
    void buildStartedAndOTELHelperIsReady() {
        assertTrue(this.otelHelper.isReady());
    }

    @Test
    void buildStartedTriggeredWithNoParentShouldCreateABuildSpanAndBeAvailableInGetSpan() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        // Stubbing this method to return the build if there is no parent, as this is the behaviour of TeamCity
        BuildPromotion[] buildPromotions = new BuildPromotion[]{build.getBuildPromotion()};
        when(build.getBuildPromotion().findTops()).thenReturn(buildPromotions);

        // Act
        this.buildListener.buildStarted(build);
        Span builtSpan = this.otelHelper.getSpan(String.valueOf(build.getBuildId()));
        Span expectedSpan = this.otelHelper.createSpan(String.valueOf(build.getBuildId()), builtSpan);

        // Assert
        assertEquals(expectedSpan, builtSpan);
        assertNotNull(builtSpan);
    }

    @Test
    void buildStartedTriggeredWithAParentShouldCreateABuildSpanAndBeAvailableInGetSpan() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        SRunningBuild parentBuild = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        // Stubbing this method to return the build if there is no parent, as this is the behaviour of TeamCity
        BuildPromotion[] buildPromotions = new BuildPromotion[]{parentBuild.getBuildPromotion()};
        when(build.getBuildPromotion().findTops()).thenReturn(buildPromotions);

        // Act
        this.buildListener.buildStarted(build);
        Span parentSpan = this.otelHelper.getParentSpan(String.valueOf(parentBuild.getBuildId()));
        Span builtSpan = this.otelHelper.getSpan(String.valueOf(build.getBuildId()));
        Span expectedSpan = this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan);

        // Assert
        assertEquals(expectedSpan, builtSpan);
        assertNotNull(parentSpan);
        assertNotNull(builtSpan);
    }

    @Test
    void buildFinishedOrInterruptedTriggeredSpanNotAvailableInGetSpan() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        // Stubbing this method to return the build if there is no parent, this is the behaviour of TeamCity
        BuildPromotion[] buildPromotions = new BuildPromotion[]{build.getBuildPromotion()};
        when(build.getBuildPromotion().findTops()).thenReturn(buildPromotions);
        this.buildListener.buildStarted(build);
        assertNotNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));

        // Act
        this.buildListener.buildFinished(build);

        // Assert
        assertNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }
}