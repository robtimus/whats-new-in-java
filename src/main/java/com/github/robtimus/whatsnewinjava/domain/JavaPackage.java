package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class JavaPackage extends VersionableJavaObject {

    private final String name;
    private final Map<String, JavaClass> javaClasses;

    JavaPackage(String name, JavaVersion since) {
        super(since);
        this.name = name;
        this.javaClasses = new TreeMap<>();
    }

    public String getName() {
        return name;
    }

    public boolean hasJavaClasses(JavaVersion since) {
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

    public Collection<JavaClass> getJavaClasses() {
        return Collections.unmodifiableCollection(javaClasses.values());
    }

    void addJavaClass(String className, JavaVersion since) {
        javaClasses.computeIfAbsent(className, k -> new JavaClass(this, className, since));
    }

    JavaClass getJavaClass(String className) {
        return javaClasses.computeIfAbsent(className, k -> new JavaClass(this, className, null));
    }
}
