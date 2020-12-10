package com.github.robtimus.whatsnewinjava.parser.model;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Objects.requireNonNull;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.JsonObject;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavaModule extends VersionableJavaObject {

    private final JavaAPI javaAPI;

    private final String name;

    private final boolean isAutomatic;

    private final Map<String, JavaPackage> javaPackages;

    JavaModule(JavaAPI javaAPI, String name, boolean isAutomatic, JavaVersion since, boolean deprecated) {
        super(since, deprecated);

        this.javaAPI = requireNonNull(javaAPI);

        this.name = requireNonNull(name);

        this.isAutomatic = isAutomatic;

        this.javaPackages = new TreeMap<>();
    }

    public JavaAPI getJavaAPI() {
        return javaAPI;
    }

    public String getName() {
        return name;
    }

    public boolean isAutomatic() {
        return isAutomatic;
    }

    public Collection<JavaPackage> getJavaPackages() {
        return unmodifiableCollection(javaPackages.values());
    }

    public void addJavaPackage(String packageName, JavaVersion since, boolean deprecated) {
        if (javaPackages.containsKey(packageName)) {
            throw new IllegalStateException(String.format("Duplicate package: %s.%s", name, packageName));
        }
        javaPackages.put(packageName, new JavaPackage(this, packageName, since, deprecated));
    }

    void addJavaPackage(JavaPackage javaPackage) {
        String packageName = javaPackage.getName();
        if (javaPackages.containsKey(packageName)) {
            throw new IllegalStateException(String.format("Duplicate package: %s.%s", name, packageName));
        }
        javaPackages.put(packageName, javaPackage);
    }

    public JavaPackage getJavaPackage(String packageName) {
        JavaPackage javaPackage = findJavaPackage(packageName);
        if (javaPackage == null) {
            throw new IllegalStateException(String.format("Could not find package %s", packageName));
        }
        return javaPackage;
    }

    public JavaPackage findJavaPackage(String packageName) {
        return javaPackages.get(packageName);
    }

    @Override
    public boolean isSince(JavaVersion javaVersion) {
        // For Java 9, don't match unless all packages are also introduced in the version
        return super.isSince(javaVersion) && (!javaVersion.introducedModules() || allPackagesSince(javaVersion));
    }

    private boolean allPackagesSince(JavaVersion javaVersion) {
        return !javaPackages.isEmpty() && javaPackages.values().stream()
                .allMatch(p -> p.isSince(javaVersion));
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    void appendToJSON(JsonObject json) {
        super.appendToJSON(json);

        JsonObject packages = new JsonObject();
        for (JavaPackage javaPackage : javaPackages.values()) {
            packages.add(javaPackage.getName(), javaPackage.toJSON());
        }
        json.add("packages", packages);
    }

    static JavaModule fromJSON(JsonObject json, JavaAPI javaAPI, String name) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        JavaModule javaModule = new JavaModule(javaAPI, name, false, since, deprecated);

        JsonObject packages = json.get("packages").getAsJsonObject();
        for (String packageName : packages.keySet()) {
            JsonObject packageJSON = packages.get(packageName).getAsJsonObject();
            JavaPackage javaPackage = JavaPackage.fromJSON(packageJSON, javaModule, packageName);
            javaModule.javaPackages.put(javaPackage.getName(), javaPackage);
        }

        return javaModule;
    }
}
