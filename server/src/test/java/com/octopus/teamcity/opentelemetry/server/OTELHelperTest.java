package com.octopus.teamcity.opentelemetry.server;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
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
    void getParentSpanTest(@Mock SRunningBuild build) {
        assertEquals(this.otelHelper.getParentSpan(String.valueOf(build.getBuildId())), this.otelHelper.getSpan(String.valueOf(build.getBuildId())) );
    }

    @Test
    void createSpanTest(@Mock SRunningBuild build) {
        Span parentSpan = this.otelHelper.getParentSpan(String.valueOf(build.getBuildId()));
        assertEquals(this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan), this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }

    @Test
    void createTransientSpanTest(@Mock SRunningBuild build) {
        Span parentSpan = this.otelHelper.getParentSpan(String.valueOf(build.getBuildId()));
        assertNotNull(this.otelHelper.createTransientSpan(String.valueOf(build.getBuildId()), parentSpan, new Date().getTime()));
    }

    @Test
    void removeSpanTest(@Mock SRunningBuild build) {
        Span parentSpan = this.otelHelper.getParentSpan(String.valueOf(build.getBuildId()));
        this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan);
        this.otelHelper.removeSpan(String.valueOf(build.getBuildId()));
        assertNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }

    @Test
    void getSpanExistsTest(@Mock SRunningBuild build) {
        Span parentSpan = this.otelHelper.getParentSpan(String.valueOf(build.getBuildId()));
        this.otelHelper.createSpan(String.valueOf(build.getBuildId()), parentSpan);
        assertNotNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }

    @Test
    void getSpanNotExistsTest(@Mock SRunningBuild build) {
        assertNull(this.otelHelper.getSpan(String.valueOf(build.getBuildId())));
    }
}