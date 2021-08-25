package com.octopus.teamcity.opentelemetry.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SRunningBuild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OTELHelperTest {

    @Mock private OpenTelemetry openTelemetry = mock(OpenTelemetry.class, RETURNS_DEEP_STUBS);
    @Mock private Tracer tracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
    @Mock private HashMap<String, Span> spanMap;
    private OTELHelper otelHelper;

    @BeforeEach
    void setUp() {
        final String ENDPOINT = "https://otel.endpoint.com";
        final Map<String, String> HEADERS = new HashMap<>() {{
            put("testHeader1", "testHeaderValue1");
            put("testHeader2", "testHeaderValue2");
        }};
        GlobalOpenTelemetry.resetForTest();
        this.otelHelper = new OTELHelper(HEADERS, ENDPOINT);
    }


    @Test
    void isReadyTest() {
        assertNotNull(this.openTelemetry);
        assertNotNull(this.tracer);
        assertNotNull(this.spanMap);
    }

    @Test
    void getParentSpanShouldReturnABuildSpanAndBeAvailableInGetSpan(@Mock SRunningBuild build) {
        // Arrange
        String parentBuildId = String.valueOf(build.getBuildId());
        Span parentSpan = this.otelHelper.getParentSpan(parentBuildId);

        // Act
        Span buildSpan = this.otelHelper.getSpan(String.valueOf(build.getBuildId()));

        // Assert
        assertNotNull(parentSpan);
        assertEquals(parentSpan, buildSpan);
    }

    @Test
    void createSpanShouldHaveSpanAvailableInGetSpan() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        Span parentSpan = createParentSpanForTest(build);

        // Act
        Span expectedSpan = this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan);
        Span actualSpan = this.otelHelper.getSpan(String.valueOf(build.getBuildId()));

        // Assert
        assertEquals(expectedSpan, actualSpan);
    }

    @Test
    void createTransientSpanShouldReturnSpanAndNotBeAvailableInGetSpan() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        SRunningBuild parentBuild = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        Span parentSpan = createParentSpanForTest(parentBuild);
        // Work around for mocked build.getBuildId(), as you cannot mock a final method (will return 0 in all cases when called), this ensures that the parent build id and the build id are different values
        // This ensures two different spans are created, a build span and a parent span
        when(build.getBuildId()).thenReturn(12345L);
        String buildId = String.valueOf(build.getBuildId());

        // Act
        Span expectedSpan = this.otelHelper.createTransientSpan(buildId, parentSpan, new Date().getTime());

        // Assert
        assertNotNull(expectedSpan);
        assertNotNull(String.valueOf(parentBuild.getBuildId()));
        assertNull(this.otelHelper.getSpan(buildId));
    }

    @Test
    void aRemovedSpanShouldNotBeAvailableInGetSpan() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        Span parentSpan = createParentSpanForTest(build);
        this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan);

        // Act
        this.otelHelper.removeSpan(String.valueOf(build.getBuildId()));

        // Assert
        assertNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }

    @Test
    void getSpanShouldReturnSpanIfExists() {
        // Arrange
        SRunningBuild build = mock(SRunningBuild.class, RETURNS_DEEP_STUBS);
        Span parentSpan = createParentSpanForTest(build);
        this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan);

        // Act
        Span expectedSpan = this.otelHelper.getSpan(String.valueOf(build.getBuildId()));

        // Assert
        assertNotNull(expectedSpan);
    }

    @Test
    void getSpanShouldNOTReturnSpanWhenItDoesNotExist(@Mock SRunningBuild build) {
        // Assert
        assertNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }

    private Span createParentSpanForTest(SRunningBuild build) {
        BuildPromotion[] buildPromotions = new BuildPromotion[]{build.getBuildPromotion()};
        when(build.getBuildPromotion().findTops()).thenReturn(buildPromotions);
        BuildPromotion[] parentBuilds = build.getBuildPromotion().findTops();
        BuildPromotion parentBuildPromotion = parentBuilds[0];
        String parentBuildId = String.valueOf(parentBuildPromotion.getId());
        return this.otelHelper.getParentSpan(parentBuildId);
    }
}