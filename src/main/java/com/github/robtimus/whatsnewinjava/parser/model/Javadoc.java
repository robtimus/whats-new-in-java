package com.github.robtimus.whatsnewinjava.parser.model;

import com.google.gson.JsonObject;

@SuppressWarnings({ "nls", "javadoc" })
public final class Javadoc {

    private final String baseURL;
    private final boolean useModules;

    public Javadoc(String baseURL, boolean useModules) {
        this.baseURL = baseURL;
        this.useModules = useModules;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public boolean useModules() {
        return useModules;
    }

    JsonObject toJSON() {
        JsonObject json = new JsonObject();
        json.addProperty("baseURL", baseURL);
        json.addProperty("useModules", useModules);
        return json;
    }

    static Javadoc fromJSON(JsonObject json) {
        String baseURL = json.get("baseURL").getAsString();
        boolean useModules = json.get("useModules").getAsBoolean();
        return new Javadoc(baseURL, useModules);
    }
}
