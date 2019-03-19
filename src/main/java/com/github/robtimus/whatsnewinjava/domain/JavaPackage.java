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

public final class JavaPackage extends VersionableJavaObject {

    private final JavaModule javaModule;
    private final String name;
    private final Map<String, JavaClass> javaClasses;

    private final Javadoc javadoc;

    JavaPackage(JavaModule javaModule, String name, JavaVersion since, boolean deprecated) {
        super(since, deprecated);
        this.javaModule = javaModule;
        this.name = name;
        this.javaClasses = new TreeMap<>();
        this.javadoc = javaModule.getJavadoc();
    }

    public JavaModule getJavaModule() {
        return javaModule;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean isDeprecated() {
        return super.isDeprecated() || (javaModule != null && javaModule.isDeprecated());
    }

    public Javadoc getJavadoc() {
        return javadoc;
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
        return c -> (c.isSince(since) && !isSince(since)) || c.hasJavaMembers(since);
    }

    void addJavaClass(String className, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures) {
        if (javaClasses.containsKey(className)) {
            throw new IllegalStateException(String.format("Duplicate class: %s.%s", name, className));
        }
        javaClasses.put(className, new JavaClass(this, className, since, deprecated, inheritedMethodSignatures));
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
        Stream<JavaVersion> classSinceValues = javaClasses.values().stream()
                .flatMap(JavaClass::allSinceValues);
        return Stream.concat(ownSince, classSinceValues)
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

    JavaPackage copy(JavaModule copyModule) {
        JavaPackage copy = new JavaPackage(copyModule, name, getSince(), super.isDeprecated());
        for (JavaClass javaClass : javaClasses.values()) {
            JavaClass classCopy = javaClass.copy(copy);
            copy.javaClasses.put(classCopy.getName(), classCopy);
        }
        return copy;
    }

    void merge(JavaPackage other) {
        for (JavaClass otherClass : other.javaClasses.values()) {
            String className = otherClass.getName();

            JavaClass javaClass = javaClasses.get(className);
            if (javaClass == null) {
                javaClasses.put(className, otherClass);
            } else {
                javaClass.merge(otherClass);
            }
        }
    }

    static void mergeAll(List<JavaPackage> javaPackages, List<JavaPackage> toMerge) {
        for (JavaPackage javaPackage : javaPackages) {
            JavaPackage packageToMerge = remove(toMerge, javaPackage.name);
            if (packageToMerge != null) {
                javaPackage.merge(packageToMerge);
            }
        }
        javaPackages.addAll(toMerge);
        javaPackages.sort(Comparator.comparing(JavaPackage::getName));
    }

    private static JavaPackage remove(List<JavaPackage> javaPackages, String name) {
        for (Iterator<JavaPackage> i = javaPackages.iterator(); i.hasNext(); ) {
            JavaPackage javaPackage = i.next();
            if (javaPackage.name.equals(name)) {
                i.remove();
                return javaPackage;
            }
        }
        return null;
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

    static JavaPackage fromJSON(JsonObject json, JavaModule javaModule, String name) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        JavaPackage javaPackage = new JavaPackage(javaModule, name, since, deprecated);

        JsonObject classes = json.get("classes").getAsJsonObject();
        for (String className : classes.keySet()) {
            JsonObject classJSON = classes.get(className).getAsJsonObject();
            JavaClass javaClass = JavaClass.fromJSON(classJSON, javaPackage, className);
            javaPackage.javaClasses.put(javaClass.getName(), javaClass);
        }

        return javaPackage;
    }
}
