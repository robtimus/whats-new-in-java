/*
 * Javadoc.java
 * Copyright 2019 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.whatsnewinjava.parser.model;

import com.google.gson.JsonObject;

@SuppressWarnings({ "nls", "javadoc" })
public record Javadoc(String baseURL, boolean useModules) {

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
