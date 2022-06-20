package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.serverSide.crypt.RSACipher;

public class HeaderDto {
    String key;
    String value;
    String type;

    public HeaderDto(String key, String value, String type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }

    public String getEncryptedValue() { return RSACipher.encryptDataForWeb(value); }
    public String getType() { return type; }
}
