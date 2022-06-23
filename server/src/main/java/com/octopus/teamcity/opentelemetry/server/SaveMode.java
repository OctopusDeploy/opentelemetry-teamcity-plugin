package com.octopus.teamcity.opentelemetry.server;

import java.util.Arrays;
import java.util.Optional;

public enum SaveMode {
    SAVE("save"),
    RESET("reset");

    private final String value;

    SaveMode(String value) {
        this.value = value;
    }

    public static Optional<SaveMode> get(String value) {
        return Arrays.stream(SaveMode.values())
                .filter(mode -> mode.value.equals(value))
                .findFirst();
    }
}
