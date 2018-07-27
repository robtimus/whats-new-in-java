package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class JavaAPI {

    private final Map<String, JavaPackage> javaPackages = new TreeMap<>();
    private final Set<JavaVersion> javaVersions = new TreeSet<>();

    public void addPackage(String packageName, JavaVersion since) {
        javaPackages.put(packageName, new JavaPackage(packageName, since));
        javaVersions.add(since);
    }

    public void addClass(String packageName, String className, JavaVersion since) {
        JavaPackage javaPackage = javaPackages.computeIfAbsent(packageName, k -> new JavaPackage(packageName, null));
        javaPackage.addJavaClass(className, since);
        javaVersions.add(since);
    }

    public void addMember(String packageName, String className, String signature, JavaVersion since) {
        JavaPackage javaPackage = javaPackages.computeIfAbsent(packageName, k -> new JavaPackage(packageName, null));
        JavaClass javaClass = javaPackage.getJavaClass(className);
        javaClass.addJavaMember(signature, since);
        javaVersions.add(since);
    }

    public Collection<JavaPackage> getJavaPackages(JavaVersion since) {
        return javaPackages.values().stream()
                .filter(matchesSince(since))
                .collect(Collectors.toList());
    }

    private Predicate<JavaPackage> matchesSince(JavaVersion since) {
        return p -> p.getSince() == since || p.hasJavaClasses(since);
    }

    public Map<JavaVersion, Collection<JavaPackage>> getPackagesPerVersion() {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, Collection<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        for (JavaVersion javaVersion : javaVersions) {
            Collection<JavaPackage> javaPackages = getJavaPackages(javaVersion);
            if (!javaPackages.isEmpty()) {
                packagesPerVersion.put(javaVersion, javaPackages);
            }
        }
        return packagesPerVersion;
    }
}
