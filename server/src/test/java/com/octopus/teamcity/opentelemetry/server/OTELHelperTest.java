package com.octopus.teamcity.opentelemetry.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OTELHelperTest {

    @Mock
    OpenTelemetry openTelemetry;

    @Mock
    Tracer tracer;

    @Mock
    HashMap<String, Span> spanMap;

    @Test
    void isReadyTest() {
        assertNotNull(openTelemetry);
        assertNotNull(tracer);
        assertNotNull(spanMap);
    }
}