package com.octopus.teamcity.opentelemetry.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import jetbrains.buildServer.Build;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.impl.BuildPromotionImpl;
import jetbrains.buildServer.serverSide.impl.DummyBuildPromotion;
import jetbrains.buildServer.util.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Array;
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
    void illegalStateExceptionWhenHeadersEmpty(@Mock EventDispatcher<BuildServerListener> buildServerListenerEventDispatcher) {
        assertThrows(IllegalStateException.class,() -> new TeamCityBuildListener(buildServerListenerEventDispatcher));
    }

    @Test
    void buildStartedAndOTELHelperIsReady() {
        assertTrue(this.otelHelper.isReady());
    }

    @Test
    void buildWithNoParent() {
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        // Stubbing this method to return the build if there is no parent, this is the behaviour of TeamCity
        BuildPromotion[] buildPromotions = new BuildPromotion[]{build.getBuildPromotion()};
        when(build.getBuildPromotion().findTops()).thenReturn(buildPromotions);
        this.buildListener.buildStarted(build);
        Span builtSpan = this.otelHelper.getSpan(String.valueOf(build.getBuildId()));
        Span expectedSpan = this.otelHelper.createSpan(String.valueOf(build.getBuildId()), builtSpan);
        assertEquals(expectedSpan, builtSpan);
    }
}