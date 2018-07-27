package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class JavaClass extends VersionableJavaObject {

    private final JavaPackage javaPackage;
    private final String name;
    private final Map<String, JavaMember> javaMembers;

    JavaClass(JavaPackage javaPackage, String name, JavaVersion since) {
        super(since);
        this.javaPackage = Objects.requireNonNull(javaPackage);
        this.name = Objects.requireNonNull(name);
        this.javaMembers = new TreeMap<>();
    }

    public JavaPackage getJavaPackage() {
        return javaPackage;
    }

    public String getName() {
        return name;
    }

    public boolean hasJavaMembers(JavaVersion since) {
        return javaMembers.values().stream()
                .anyMatch(matchesSince(since));
    }

    public Collection<JavaMember> getJavaMembers(JavaVersion since) {
        return javaMembers.values().stream()
                .filter(matchesSince(since))
                .collect(Collectors.toList());
    }

    private Predicate<JavaMember> matchesSince(JavaVersion since) {
        return m -> m.getSince() == since && !since.equals(this.getSince());
    }

    void addJavaMember(String signature, JavaVersion since) {
        javaMembers.put(signature, new JavaMember(this, signature, since));
    }

    JavaMember getJavaMember(String signature, JavaVersion since) {
        return javaMembers.computeIfAbsent(signature, k -> new JavaMember(this, signature, since));
    }
}
