package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.gson.JsonObject;

public final class JavaModule extends VersionableJavaObject {

    private final String name;
    private final Map<String, JavaPackage> javaPackages;

    private final Javadoc javadoc;

    JavaModule(String name, JavaVersion since, boolean deprecated, Javadoc javadoc) {
        super(since, deprecated);
        this.name = name;
        this.javaPackages = new TreeMap<>();
        this.javadoc = javadoc;
    }

    public String getName() {
        return name;
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

    public Javadoc getJavadoc() {
        return javadoc;
    }

    public Collection<JavaPackage> getJavaPackages() {
        return Collections.unmodifiableCollection(javaPackages.values());
    }

    boolean hasJavaPackages() {
        return !javaPackages.isEmpty();
    }

    boolean hasJavaPackages(JavaVersion since) {
        return javaPackages.values().stream()
                .anyMatch(matchesSince(since));
    }

    public Collection<JavaPackage> getJavaPackages(JavaVersion since) {
        return javaPackages.values().stream()
                .filter(matchesSince(since))
                .collect(Collectors.toList());
    }

    private Predicate<JavaPackage> matchesSince(JavaVersion since) {
        return p -> (p.isSince(since) && !isSince(since)) || p.hasJavaClasses(since);
    }

    void addJavaPackage(JavaPackage javaPackage) {
        String packageName = javaPackage.getName();
        if (javaPackages.containsKey(packageName)) {
            throw new IllegalStateException(String.format("Duplicate package: %s.%s", name, packageName));
        }
        javaPackages.put(packageName, javaPackage);
    }

    void addJavaPackage(String packageName, JavaVersion since, boolean deprecated) {
        if (javaPackages.containsKey(packageName)) {
            throw new IllegalStateException(String.format("Duplicate package: %s.%s", name, packageName));
        }
        javaPackages.put(packageName, new JavaPackage(this, packageName, since, deprecated));
    }

    JavaPackage getJavaPackage(String packageName) {
        JavaPackage javaPackage = javaPackages.get(packageName);
        if (javaPackage == null) {
            throw new IllegalStateException(String.format("Could not find package %s.%s", name, packageName));
        }
        return javaPackage;
    }

    JavaPackage findJavaPackage(String packageName) {
        return javaPackages.get(packageName);
    }

    Stream<JavaVersion> allSinceValues() {
        Stream<JavaVersion> ownSince = Stream.of(getSince());
        Stream<JavaVersion> packageSinceValues = javaPackages.values().stream()
                .flatMap(JavaPackage::allSinceValues);
        return Stream.concat(ownSince, packageSinceValues)
                .filter(Objects::nonNull);
    }

    void retainSince(JavaVersion minimalJavaVersion) {
        javaPackages.values().forEach(c -> c.retainSince(minimalJavaVersion));
        javaPackages.values().removeIf(c -> !c.hasJavaClasses() && !c.hasMinimalSince(minimalJavaVersion));
    }

    @Override
    public String toString() {
        return name;
    }

    JavaModule copy() {
        JavaModule copy = new JavaModule(name, getSince(), isDeprecated(), javadoc);
        for (JavaPackage javaPackage : javaPackages.values()) {
            JavaPackage packageCopy = javaPackage.copy(copy);
            copy.javaPackages.put(packageCopy.getName(), packageCopy);
        }
        return copy;
    }

    void merge(JavaModule other) {
        for (JavaPackage otherPackage : other.javaPackages.values()) {
            String packageName = otherPackage.getName();

            JavaPackage javaPackage = javaPackages.get(packageName);
            if (javaPackage == null) {
                javaPackages.put(packageName, otherPackage);
            } else {
                javaPackage.merge(otherPackage);
            }
        }
    }

    static void mergeAll(List<JavaModule> javaModules, List<JavaModule> toMerge) {
        for (JavaModule javaModule : javaModules) {
            JavaModule moduleToMerge = remove(toMerge, javaModule.name);
            if (moduleToMerge != null) {
                javaModule.merge(moduleToMerge);
            }
        }
        javaModules.addAll(toMerge);
        javaModules.sort(Comparator.comparing(JavaModule::getName));
    }

    private static JavaModule remove(List<JavaModule> javaModules, String name) {
        for (Iterator<JavaModule> i = javaModules.iterator(); i.hasNext(); ) {
            JavaModule javaModule = i.next();
            if (javaModule.name.equals(name)) {
                i.remove();
                return javaModule;
            }
        }
        return null;
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

    static JavaModule fromJSON(JsonObject json, String name, Javadoc javadoc) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        JavaModule javaModule = new JavaModule(name, since, deprecated, javadoc);

        JsonObject packages = json.get("packages").getAsJsonObject();
        for (String packageName : packages.keySet()) {
            JsonObject packageJSON = packages.get(packageName).getAsJsonObject();
            JavaPackage javaPackage = JavaPackage.fromJSON(packageJSON, javaModule, packageName);
            javaModule.javaPackages.put(javaPackage.getName(), javaPackage);
        }

        return javaModule;
    }
}
