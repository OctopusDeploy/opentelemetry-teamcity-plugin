package com.octopus.teamcity.opentelemetry.server;

import java.util.Arrays;
import java.util.Optional;

public enum OTELService {
    HONEYCOMB("honeycomb.io"),
    CUSTOM("custom");

    private final String value;

    OTELService(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Optional<OTELService> get(String value) {
        return Arrays.stream(OTELService.values())
                .filter(service -> service.value.equals(value))
                .findFirst();
    }
}
