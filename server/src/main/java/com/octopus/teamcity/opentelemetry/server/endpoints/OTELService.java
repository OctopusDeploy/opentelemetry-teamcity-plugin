package com.octopus.teamcity.opentelemetry.server.endpoints;

import java.util.Arrays;
import java.util.Optional;

public enum OTELService {
    HONEYCOMB("honeycomb.io"),
    ZIPKIN("zipkin.io"),
    CUSTOM("custom");

    private final String value;

    OTELService(String value) {
        this.value = value;
    }

    public static String readableJoin() {
        var sb = new StringBuilder();
        var values = OTELService.values();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i].value);
            if (i < values.length - 2) {
                sb.append(", ");
            } else if (i == values.length - 2) {
                sb.append(" or ");
            }
        }
        return sb.toString();
    }

    public String getValue() {
        return value;
    }

    public static Optional<OTELService> get(String value) {
        return Arrays.stream(OTELService.values())
                .filter(service -> service.value.equals(value))
                .findFirst();
    }

    public static OTELService getDefault()
    {
        return OTELService.HONEYCOMB;
    }
}

