package com.github.robtimus.whatsnewinjava.domain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class VersionableJavaObject {

    private final JavaVersion since;
    private final boolean deprecated;

    VersionableJavaObject(JavaVersion since, boolean deprecated) {
        this.since = since;
        this.deprecated = deprecated;
    }

    public JavaVersion getSince() {
        return since;
    }

    public boolean isSince(JavaVersion javaVersion) {
        return javaVersion.equals(since);
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    boolean hasMinimalSince(JavaVersion minimalJavaVersion) {
        return since != null && since.compareTo(minimalJavaVersion) >= 0;
    }

    final JsonObject toJSON() {
        JsonObject json = new JsonObject();
        appendToJSON(json);
        return json;
    }

    void appendToJSON(JsonObject json) {
        if (since != null) {
            json.addProperty("since", since.toString());
        }
        if (deprecated) {
            json.addProperty("deprecated", true);
        }
    }

    static JavaVersion readSince(JsonObject json) {
        JsonElement since = json.get("since");
        return since == null ? null : JavaVersion.parse(since.getAsString());
    }

    static boolean readDeprecated(JsonObject json) {
        JsonElement deprecated = json.get("deprecated");
        return deprecated != null && deprecated.getAsBoolean();
    }
}
