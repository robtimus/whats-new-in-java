/*
 * JavaAPI.java
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

import static java.util.Collections.unmodifiableCollection;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavaAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaAPI.class);

    private static final String AUTOMATIC_MODULE_NAME = "<automatic>";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Map<String, JavaModule> javaModules = new TreeMap<>(nullsFirst(naturalOrder()));

    private final Javadoc javadoc;

    public JavaAPI(Javadoc javadoc) {
        this.javadoc = requireNonNull(javadoc);
    }

    public Collection<JavaModule> javaModules() {
        return unmodifiableCollection(javaModules.values());
    }

    public boolean hasAutomaticModule() {
        return javaModules.containsKey(AUTOMATIC_MODULE_NAME);
    }

    public void addJavaModule(String moduleName, JavaVersion since, boolean deprecated) {
        if (javaModules.containsKey(AUTOMATIC_MODULE_NAME)) {
            throw new IllegalStateException("Cannot add module when automatic module is defined: %s".formatted(moduleName));
        }
        if (javaModules.containsKey(moduleName)) {
            throw new IllegalStateException("Duplicate module: %s".formatted(moduleName));
        }
        javaModules.put(moduleName, new JavaModule(this, moduleName, false, since, deprecated));
    }

    public void addAutomaticJavaModule() {
        if (javaModules.containsKey(AUTOMATIC_MODULE_NAME)) {
            throw new IllegalStateException("Automatic module is already defined");
        }
        if (!javaModules.isEmpty()) {
            throw new IllegalStateException("Cannot add automatic module if other modules are defined");
        }
        javaModules.put(AUTOMATIC_MODULE_NAME, new JavaModule(this, AUTOMATIC_MODULE_NAME, true, null, false));
    }

    public JavaModule getJavaModule(String moduleName) {
        JavaModule javaModule = findJavaModule(moduleName);
        if (javaModule == null) {
            throw new IllegalStateException("Could not find module %s".formatted(moduleName));
        }
        return javaModule;
    }

    public JavaModule getAutomaticJavaModule() {
        JavaModule javaModule = findAutomaticJavaModule();
        if (javaModule == null) {
            throw new IllegalStateException("Could not find automatic module");
        }
        return javaModule;
    }

    public JavaModule findJavaModule(String moduleName) {
        return javaModules.get(moduleName);
    }

    public JavaModule findAutomaticJavaModule() {
        return javaModules.get(AUTOMATIC_MODULE_NAME);
    }

    public Collection<JavaPackage> javaPackages() {
        return javaModules.values().stream()
                .flatMap(m -> m.javaPackages().stream())
                .toList();
    }

    public JavaPackage findJavaPackage(String packageName) {
        return javaModules.values().stream()
                .flatMap(m -> m.javaPackages().stream())
                .filter(p -> p.name().equals(packageName))
                .findFirst()
                .orElse(null);
    }

    public Javadoc javadoc() {
        return javadoc;
    }

    public void toJSON(Writer writer) {
        JsonObject json = new JsonObject();
        json.add("javadoc", javadoc.toJSON());

        JavaModule automaticModule = javaModules.get(AUTOMATIC_MODULE_NAME);
        if (automaticModule == null) {
            JsonObject modules = new JsonObject();
            for (JavaModule javaModule : javaModules.values()) {
                modules.add(javaModule.name(), javaModule.toJSON());
            }
            json.add("modules", modules);
        } else {
            JsonObject packages = new JsonObject();
            for (JavaPackage javaPackage : automaticModule.javaPackages()) {
                packages.add(javaPackage.name(), javaPackage.toJSON());
            }
            json.add("packages", packages);
        }

        GSON.toJson(json, writer);
    }

    public static JavaAPI fromJSON(Reader reader) {
        JsonObject json = (JsonObject) JsonParser.parseReader(reader);

        Javadoc javadoc = Javadoc.fromJSON(json.get("javadoc").getAsJsonObject());

        JavaAPI javaAPI = new JavaAPI(javadoc);

        if (json.has("packages")) {
            JavaModule automaticModule = new JavaModule(javaAPI, AUTOMATIC_MODULE_NAME, true, null, false);
            javaAPI.javaModules.put(AUTOMATIC_MODULE_NAME, automaticModule);

            JsonObject packages = json.get("packages").getAsJsonObject();
            for (String packageName : packages.keySet()) {
                JsonObject packageJSON = packages.get(packageName).getAsJsonObject();
                JavaPackage javaPackage = JavaPackage.fromJSON(packageJSON, automaticModule, packageName);
                automaticModule.addJavaPackage(javaPackage);
            }
        } else {
            JsonObject modules = json.get("modules").getAsJsonObject();
            for (String moduleName : modules.keySet()) {
                JsonObject moduleJSON = modules.get(moduleName).getAsJsonObject();
                JavaModule javaModule = JavaModule.fromJSON(moduleJSON, javaAPI, moduleName);
                javaAPI.javaModules.put(javaModule.name(), javaModule);
            }
        }

        return javaAPI;
    }

    public static NavigableMap<JavaVersion, JavaAPI> allFromJSON(Path baseDir) throws IOException {
        try (Stream<Path> stream = Files.walk(baseDir, 1)) {
            Map<JavaVersion, JavaAPI> javaAPIs = stream
                    .filter(Files::isRegularFile)
                    .filter(JavaAPI::isJavaAPIFile)
                    .map(JavaAPI::fromJSON)
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

            LOGGER.info("Loaded all Java APIs");

            return new TreeMap<>(javaAPIs);
        }
    }

    private static boolean isJavaAPIFile(Path file) {
        return file.getFileName().toString().matches("java-\\d+\\.json");
    }

    private static Map.Entry<JavaVersion, JavaAPI> fromJSON(Path jsonFile) {
        LOGGER.info("Loading Java API from file {}", jsonFile);

        String javaVersionString = jsonFile.getFileName().toString()
                .replaceFirst("^java-", "")
                .replaceFirst("\\.json$", "");
        JavaVersion javaVersion = JavaVersion.parse(javaVersionString);
        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JavaAPI javaAPI = fromJSON(reader);
            return new AbstractMap.SimpleImmutableEntry<>(javaVersion, javaAPI);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
