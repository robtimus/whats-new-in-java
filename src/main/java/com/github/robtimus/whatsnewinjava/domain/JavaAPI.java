package com.github.robtimus.whatsnewinjava.domain;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class JavaAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaAPI.class);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final String javadocBaseURL;
    private final Map<String, JavaPackage> javaPackages = new TreeMap<>();
    private final Set<JavaVersion> javaVersions = new TreeSet<>();

    public JavaAPI(String javadocBaseURL) {
        this.javadocBaseURL = javadocBaseURL;
    }

    public String getJavadocBaseURL() {
        return javadocBaseURL;
    }

    public void addPackage(String packageName, JavaVersion since, boolean deprecated) {
        if (javaPackages.containsKey(packageName)) {
            throw new IllegalStateException(String.format("Duplicate package: %s", packageName));
        }
        javaPackages.put(packageName, new JavaPackage(packageName, since, deprecated, javadocBaseURL));
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private void addPackageIfNotExists(String packageName, JavaVersion since, boolean deprecated) {
        if (!javaPackages.containsKey(packageName)) {
            javaPackages.put(packageName, new JavaPackage(packageName, since, deprecated, javadocBaseURL));
        }
    }

    public void addClass(String packageName, String className, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures) {
        JavaPackage javaPackage = getJavaPackage(packageName);
        javaPackage.addJavaClass(className, since, deprecated, inheritedMethodSignatures);
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private void addClassIfNotExists(String packageName, String className, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures) {
        JavaPackage javaPackage = getJavaPackage(packageName);
        if (javaPackage.findJavaClass(className) == null) {
            javaPackage.addJavaClass(className, since, deprecated, inheritedMethodSignatures);
        }
    }

    public void addMember(String packageName, String className, JavaMember.Type type, String signature, JavaVersion since, boolean deprecated) {
        JavaPackage javaPackage = getJavaPackage(packageName);
        JavaClass javaClass = javaPackage.getJavaClass(className);
        javaClass.addJavaMember(type, signature, since, deprecated);
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private JavaPackage getJavaPackage(String packageName) {
        JavaPackage javaPackage = javaPackages.get(packageName);
        if (javaPackage == null) {
            throw new IllegalStateException(String.format("Could not find package %s", packageName));
        }
        return javaPackage;
    }

    private JavaPackage findJavaPackage(String packageName) {
        return javaPackages.get(packageName);
    }

    public void retainSince(JavaVersion minimalJavaVersion) {
        javaVersions.removeIf(v -> minimalJavaVersion.compareTo(v) > 0);
        javaPackages.values().forEach(p -> p.retainSince(minimalJavaVersion));
        javaPackages.values().removeIf(p -> !p.hasJavaClasses() && !p.hasMinimalSince(minimalJavaVersion));
    }

    private Collection<JavaPackage> getJavaPackages(JavaVersion since) {
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

    public void toJSON(Writer writer) {
        JsonObject json = new JsonObject();
        json.addProperty("javadocBaseURL", javadocBaseURL);

        JsonObject packages = new JsonObject();
        for (JavaPackage javaPackage : javaPackages.values()) {
            packages.add(javaPackage.getName(), javaPackage.toJSON());
        }
        json.add("packages", packages);

        GSON.toJson(json, writer);
    }

    public static JavaAPI fromJSON(Reader reader) {
        JsonObject json = (JsonObject) new JsonParser().parse(reader);

        String javadocBaseURL = json.get("javadocBaseURL").getAsString();

        JavaAPI javaAPI = new JavaAPI(javadocBaseURL);

        JsonObject packages = json.get("packages").getAsJsonObject();
        for (String packageName : packages.keySet()) {
            JsonObject packageJSON = packages.get(packageName).getAsJsonObject();
            JavaPackage javaPackage = JavaPackage.fromJSON(packageJSON, packageName, javadocBaseURL);
            javaAPI.javaPackages.put(javaPackage.getName(), javaPackage);
        }
        Set<JavaVersion> javaversions = javaAPI.javaPackages.values().stream()
                .flatMap(JavaPackage::allSinceValues)
                .collect(Collectors.toSet());
        javaAPI.javaVersions.addAll(javaversions);

        return javaAPI;
    }

    public static NavigableMap<JavaVersion, JavaAPI> allFromJSON(Path baseDir) throws IOException {
        Map<JavaVersion, JavaAPI> javaAPIs = Files.walk(baseDir, 1)
                .filter(Files::isRegularFile)
                .filter(JavaAPI::isJavaAPIFile)
                .map(JavaAPI::fromJSON)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        LOGGER.info("Loaded all Java APIs");

        return new TreeMap<>(javaAPIs);
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

    public static Map<JavaVersion, Collection<JavaPackage>> getDeprecatedPackagesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, Collection<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            JavaAPI javaAPI = new JavaAPI(current.getValue().getJavadocBaseURL());
            collectDeprecatedPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
            packagesPerVersion.put(current.getKey(), javaAPI.javaPackages.values());
            current = previous;
        }
        return packagesPerVersion;
    }

    private static void collectDeprecatedPackages(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaPackage currentPackage : current.javaPackages.values()) {
            JavaPackage previousPackage = previous.findJavaPackage(currentPackage.getName());
            if (previousPackage != null) {
                if (currentPackage.isDeprecated() && !previousPackage.isDeprecated()) {
                    // the entire package became deprecated
                    dest.addPackage(currentPackage.getName(), since, true);
                } else {
                    // either the package's deprecation status has not changed, or it became non-deprecated; check classes
                    collectDeprecatedClasses(currentPackage, previousPackage, since, dest);
                }
            }
            // else currentPackage is new
        }
    }

    private static void collectDeprecatedClasses(JavaPackage currentPackage, JavaPackage previousPackage, JavaVersion since, JavaAPI dest) {
        for (JavaClass currentClass : currentPackage.getJavaClasses()) {
            JavaClass previousClass = previousPackage.findJavaClass(currentClass.getName());
            if (previousClass != null) {
                if (currentClass.isDeprecated() && !previousClass.isDeprecated()) {
                    // the entire class became deprecated
                    dest.addPackageIfNotExists(currentPackage.getName(), null, currentPackage.isDeprecated());
                    dest.addClass(currentPackage.getName(), currentClass.getName(), since, true, currentClass.getInheritedMethodSignatures());
                } else {
                    // either the package's deprecation status has not changed, or it became non-deprecated; check members
                    collectDeprecatedMembers(currentPackage, currentClass, previousClass, since, dest);
                }
            }
            // else currentClass is new
        }
    }

    private static void collectDeprecatedMembers(JavaPackage currentPackage, JavaClass currentClass, JavaClass previousClass, JavaVersion since, JavaAPI dest) {
        for (JavaMember currentMember : currentClass.getJavaMembers()) {
            JavaMember previousMember = previousClass.findJavaMember(currentMember.getType(), currentMember.getSignature());
            if (previousMember != null && currentMember.isDeprecated() && !previousMember.isDeprecated()) {
                // the member became deprecated
                dest.addPackageIfNotExists(currentPackage.getName(), null, currentPackage.isDeprecated());
                dest.addClassIfNotExists(currentPackage.getName(), currentClass.getName(), null, currentClass.isDeprecated(), currentClass.getInheritedMethodSignatures());
                dest.addMember(currentPackage.getName(), currentClass.getName(), currentMember.getType(), currentMember.getSignature(), since, true);
            }
            // else currentMember is new, or the member's deprecation status has not change, or it became non-deprecated
        }
    }

    public static Map<JavaVersion, Collection<JavaPackage>> getRemovedPackagesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, Collection<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            JavaAPI javaAPI = new JavaAPI(previous.getValue().getJavadocBaseURL());
            collectRemovedPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
            packagesPerVersion.put(current.getKey(), javaAPI.javaPackages.values());
            current = previous;
        }
        return packagesPerVersion;
    }

    private static void collectRemovedPackages(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaPackage previousPackage : previous.javaPackages.values()) {
            JavaPackage currentPackage = current.findJavaPackage(previousPackage.getName());
            if (currentPackage == null) {
                // the entire package was removed
                dest.addPackage(previousPackage.getName(), since, previousPackage.isDeprecated());
            } else {
                // check classes
                collectRemovedClasses(currentPackage, previousPackage, since, dest);
            }
        }
    }

    private static void collectRemovedClasses(JavaPackage currentPackage, JavaPackage previousPackage, JavaVersion since, JavaAPI dest) {
        for (JavaClass previousClass : previousPackage.getJavaClasses()) {
            JavaClass currentClass = currentPackage.findJavaClass(previousClass.getName());
            if (currentClass == null) {
                // the entire class was removed
                dest.addPackageIfNotExists(currentPackage.getName(), null, currentPackage.isDeprecated());
                dest.addClass(currentPackage.getName(), previousClass.getName(), since, previousClass.isDeprecated(), previousClass.getInheritedMethodSignatures());
            } else {
                // check members
                collectRemovedMembers(currentPackage, currentClass, previousClass, since, dest);
            }
        }
    }

    private static void collectRemovedMembers(JavaPackage currentPackage, JavaClass currentClass, JavaClass previousClass, JavaVersion since, JavaAPI dest) {
        for (JavaMember previousMember : previousClass.getJavaMembers()) {
            JavaMember currentMember = currentClass.findJavaMember(previousMember.getType(), previousMember.getSignature());
            if (currentMember == null && (previousMember.getType() != JavaMember.Type.METHOD || !currentClass.isInheritedMethod(previousMember.getSignature()))) {
                // the member was removed
                dest.addPackageIfNotExists(currentPackage.getName(), null, currentPackage.isDeprecated());
                dest.addClassIfNotExists(currentPackage.getName(), currentClass.getName(), null, currentClass.isDeprecated(), currentClass.getInheritedMethodSignatures());
                dest.addMember(currentPackage.getName(), currentClass.getName(), previousMember.getType(), previousMember.getSignature(), since, previousMember.isDeprecated());
            }
            // else currentMember still exists
        }
    }
}
