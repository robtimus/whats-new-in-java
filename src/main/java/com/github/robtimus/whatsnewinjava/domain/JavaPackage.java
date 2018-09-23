package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.gson.JsonObject;

public final class JavaPackage extends VersionableJavaObject {

    private final String name;
    private final Map<String, JavaClass> javaClasses;

    private final String javadocBaseURL;

    JavaPackage(String name, JavaVersion since, boolean deprecated, String javadocBaseURL) {
        super(since, deprecated);
        this.name = name;
        this.javaClasses = new TreeMap<>();
        this.javadocBaseURL = javadocBaseURL;
    }

    public String getName() {
        return name;
    }

    public String getJavadocBaseURL() {
        return javadocBaseURL;
    }

    public Collection<JavaClass> getJavaClasses() {
        return Collections.unmodifiableCollection(javaClasses.values());
    }

    boolean hasJavaClasses() {
        return !javaClasses.isEmpty();
    }

    boolean hasJavaClasses(JavaVersion since) {
        return javaClasses.values().stream()
                .anyMatch(matchesSince(since));
    }

    public Collection<JavaClass> getJavaClasses(JavaVersion since) {
        return javaClasses.values().stream()
                .filter(matchesSince(since))
                .collect(Collectors.toList());
    }

    private Predicate<JavaClass> matchesSince(JavaVersion since) {
        return c -> (c.getSince() == since && !since.equals(this.getSince())) || c.hasJavaMembers(since);
    }

    void addJavaClass(String className, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures) {
        if (javaClasses.containsKey(className)) {
            throw new IllegalStateException(String.format("Duplicate class: %s.%s", name, className));
        }
        javaClasses.put(className, new JavaClass(this, className, since, deprecated, inheritedMethodSignatures, javadocBaseURL));
    }

    JavaClass getJavaClass(String className) {
        JavaClass javaClass = javaClasses.get(className);
        if (javaClass == null) {
            throw new IllegalStateException(String.format("Could not find class %s.%s", name, className));
        }
        return javaClass;
    }

    JavaClass findJavaClass(String className) {
        return javaClasses.get(className);
    }

    Stream<JavaVersion> allSinceValues() {
        Stream<JavaVersion> ownSince = Stream.of(getSince());
        Stream<JavaVersion> memberSinceValues = javaClasses.values().stream()
                .flatMap(JavaClass::allSinceValues);
        return Stream.concat(ownSince, memberSinceValues)
                .filter(Objects::nonNull);
    }

    void retainSince(JavaVersion minimalJavaVersion) {
        javaClasses.values().forEach(c -> c.retainSince(minimalJavaVersion));
        javaClasses.values().removeIf(c -> !c.hasJavaMembers() && !c.hasMinimalSince(minimalJavaVersion));
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    void appendToJSON(JsonObject json) {
        super.appendToJSON(json);

        JsonObject classes = new JsonObject();
        for (JavaClass javaClass : javaClasses.values()) {
            classes.add(javaClass.getName(), javaClass.toJSON());
        }
        json.add("classes", classes);
    }

    static JavaPackage fromJSON(JsonObject json, String name, String javadocBaseURL) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        JavaPackage javaPackage = new JavaPackage(name, since, deprecated, javadocBaseURL);

        JsonObject classes = json.get("classes").getAsJsonObject();
        for (String className : classes.keySet()) {
            JsonObject classJSON = classes.get(className).getAsJsonObject();
            JavaClass javaClass = JavaClass.fromJSON(classJSON, javaPackage, className);
            javaPackage.javaClasses.put(javaClass.getName(), javaClass);
        }

        return javaPackage;
    }
}
