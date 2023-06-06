package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelper;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelperFactory;
import com.octopus.teamcity.opentelemetry.server.helpers.OTELHelperImpl;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.SpanProcessor;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.impl.TeamCityNodesImpl;
import jetbrains.buildServer.util.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamCityBuildListenerTest {

    private OTELHelper otelHelper;
    private TeamCityBuildListener buildListener;
    private OTELHelperFactory factory;

    @BeforeEach
    void setUp(@Mock EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher) {
        GlobalOpenTelemetry.resetForTest();
        this.otelHelper = new OTELHelperImpl(mock(SpanProcessor.class, RETURNS_DEEP_STUBS));
        this.factory = mock(OTELHelperFactory.class, RETURNS_DEEP_STUBS);

        var buildStorageManager = mock(BuildStorageManager.class, RETURNS_DEEP_STUBS);
        var teamCityNodes = mock(TeamCityNodesImpl.class, RETURNS_DEEP_STUBS);
        this.buildListener = new TeamCityBuildListener(buildServerListenerEventDispatcher, factory, buildStorageManager, teamCityNodes);
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
        when(factory.getOTELHelper(Arrays.stream(buildPromotions).findFirst().get())).thenReturn(otelHelper);

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
        when(factory.getOTELHelper(Arrays.stream(buildPromotions).findFirst().get())).thenReturn(otelHelper);
        this.buildListener.buildStarted(build);
        assertNotNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));

        // Act
        this.buildListener.buildFinished(build);

        // Assert
        assertNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }

    @Test
    void buildFinishedOrInterruptedAsksFactoryToRelease() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        // Stubbing this method to return the build if there is no parent, this is the behaviour of TeamCity
        BuildPromotion[] buildPromotions = new BuildPromotion[]{build.getBuildPromotion()};
        when(build.getBuildPromotion().findTops()).thenReturn(buildPromotions);
        when(factory.getOTELHelper(Arrays.stream(buildPromotions).findFirst().get())).thenReturn(otelHelper);
        this.buildListener.buildStarted(build);
        assertNotNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));

        // Act
        this.buildListener.buildFinished(build);

        // Assert
        verify(factory, times(1)).release(build.getBuildId());
    }
}