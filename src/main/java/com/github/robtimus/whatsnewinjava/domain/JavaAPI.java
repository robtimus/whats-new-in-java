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
import java.util.List;
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

    private final Map<String, JavaModule> javaModules = new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder()));
    private final Set<JavaVersion> javaVersions = new TreeSet<>();

    private final Javadoc javadoc;

    public JavaAPI(Javadoc javadoc) {
        this.javadoc = javadoc;
    }

    public Javadoc getJavadoc() {
        return javadoc;
    }

    public void addModule(String moduleName, JavaVersion since, boolean deprecated) {
        if (javaModules.containsKey(moduleName)) {
            throw new IllegalStateException(String.format("Duplicate module: %s", moduleName));
        }
        javaModules.put(moduleName, new JavaModule(moduleName, since, deprecated, javadoc));
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private void addModuleIfNotExists(String moduleName, JavaVersion since, boolean deprecated) {
        if (!javaModules.containsKey(moduleName)) {
            javaModules.put(moduleName, new JavaModule(moduleName, since, deprecated, javadoc));
        }
    }

    public void addPackage(String moduleName, String packageName, JavaVersion since, boolean deprecated) {
        JavaModule javaModule = getJavaModule(moduleName);
        javaModule.addJavaPackage(packageName, since, deprecated);
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private void addPackageIfNotExists(String moduleName, String packageName, JavaVersion since, boolean deprecated) {
        JavaModule javaModule = getJavaModule(moduleName);
        if (javaModule.findJavaPackage(packageName) == null) {
            javaModule.addJavaPackage(packageName, since, deprecated);
        }
    }

    public void addClass(String moduleName, String packageName, String className, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures) {
        JavaModule javaModule = getJavaModule(moduleName);
        JavaPackage javaPackage = javaModule.getJavaPackage(packageName);
        javaPackage.addJavaClass(className, since, deprecated, inheritedMethodSignatures);
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private void addClassIfNotExists(String moduleName, String packageName, String className, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures) {
        JavaModule javaModule = getJavaModule(moduleName);
        JavaPackage javaPackage = javaModule.getJavaPackage(packageName);
        if (javaPackage.findJavaClass(className) == null) {
            javaPackage.addJavaClass(className, since, deprecated, inheritedMethodSignatures);
        }
    }

    public void addMember(String moduleName, String packageName, String className, JavaMember.Type type, String signature, JavaVersion since, boolean deprecated) {
        JavaModule javaModule = getJavaModule(moduleName);
        JavaPackage javaPackage = javaModule.getJavaPackage(packageName);
        JavaClass javaClass = javaPackage.getJavaClass(className);
        javaClass.addJavaMember(type, signature, since, deprecated);
        if (since != null) {
            javaVersions.add(since);
        }
    }

    private JavaModule getJavaModule(String moduleName) {
        JavaModule javaModule = javaModules.get(moduleName);
        if (javaModule == null) {
            throw new IllegalStateException(String.format("Could not find module %s", moduleName));
        }
        return javaModule;
    }

    public JavaModule findJavaModule(String moduleName) {
        return javaModules.get(moduleName);
    }

    public JavaPackage findJavaPackage(String packageName) {
        return javaModules.values().stream()
                .flatMap(m -> m.getJavaPackages().stream())
                .filter(p -> p.getName().equals(packageName))
                .findFirst()
                .orElse(null);
    }

    public void retainSince(JavaVersion minimalJavaVersion) {
        javaVersions.removeIf(v -> minimalJavaVersion.compareTo(v) > 0);
        javaModules.values().forEach(p -> p.retainSince(minimalJavaVersion));
        javaModules.values().removeIf(p -> !p.hasJavaPackages() && !p.hasMinimalSince(minimalJavaVersion));
    }

    private Predicate<JavaModule> matchesSince(JavaVersion since) {
        return m -> m.isSince(since) || m.hasJavaPackages(since);
    }

    private JavaAPI copy() {
        JavaAPI copy = new JavaAPI(javadoc);
        for (JavaModule javaModule : javaModules.values()) {
            JavaModule moduleCopy = javaModule.copy();
            copy.javaModules.put(moduleCopy.getName(), moduleCopy);
        }
        Set<JavaVersion> javaversions = copy.javaModules.values().stream()
                .flatMap(JavaModule::allSinceValues)
                .collect(Collectors.toSet());
        copy.javaVersions.addAll(javaversions);
        return copy;
    }

    private void merge(JavaAPI other) {
        JavaModule otherNoModule = other.javaModules.get(null);
        if (otherNoModule == null) {
            for (JavaModule otherModule : other.javaModules.values()) {
                String moduleName = otherModule.getName();

                JavaModule javaModule = javaModules.get(moduleName);
                if (javaModule == null) {
                    javaModules.put(moduleName, otherModule);
                } else {
                    javaModule.merge(otherModule);
                }
            }
        } else {
            for (JavaPackage otherPackage : otherNoModule.getJavaPackages()) {
                String packageName = otherPackage.getName();

                JavaPackage javaPackage = findJavaPackage(packageName);
                if (javaPackage == null) {
                    JavaModule noModule = javaModules.get(null);
                    if (noModule == null) {
                        noModule = new JavaModule(null, null, false, otherNoModule.getJavadoc());
                        javaModules.put(null, noModule);
                    }
                    noModule.addJavaPackage(otherPackage);
                } else {
                    javaPackage.merge(otherPackage);
                }
            }
        }
    }

    public void toJSON(Writer writer) {
        JsonObject json = new JsonObject();
        json.add("javadoc", javadoc.toJSON());

        JavaModule noModule = javaModules.get(null);
        if (noModule == null) {
            JsonObject modules = new JsonObject();
            for (JavaModule javaModule : javaModules.values()) {
                modules.add(javaModule.getName(), javaModule.toJSON());
            }
            json.add("modules", modules);
        } else {
            JsonObject packages = new JsonObject();
            for (JavaPackage javaPackage : noModule.getJavaPackages()) {
                packages.add(javaPackage.getName(), javaPackage.toJSON());
            }
            json.add("packages", packages);
        }

        GSON.toJson(json, writer);
    }

    public static JavaAPI fromJSON(Reader reader) {
        JsonObject json = (JsonObject) new JsonParser().parse(reader);

        Javadoc javadoc = Javadoc.fromJSON(json.get("javadoc").getAsJsonObject());

        JavaAPI javaAPI = new JavaAPI(javadoc);

        if (json.has("packages")) {
            JavaModule noModule = new JavaModule(null, null, false, javadoc);
            javaAPI.javaModules.put(null, noModule);

            JsonObject packages = json.get("packages").getAsJsonObject();
            for (String packageName : packages.keySet()) {
                JsonObject packageJSON = packages.get(packageName).getAsJsonObject();
                JavaPackage javaPackage = JavaPackage.fromJSON(packageJSON, noModule, packageName);
                noModule.addJavaPackage(javaPackage);
            }
        } else {
            JsonObject modules = json.get("modules").getAsJsonObject();
            for (String moduleName : modules.keySet()) {
                JsonObject moduleJSON = modules.get(moduleName).getAsJsonObject();
                JavaModule javaModule = JavaModule.fromJSON(moduleJSON, moduleName, javadoc);
                javaAPI.javaModules.put(javaModule.getName(), javaModule);
            }
        }
        Set<JavaVersion> javaversions = javaAPI.javaModules.values().stream()
                .flatMap(JavaModule::allSinceValues)
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

    private Collection<JavaModule> getJavaModules(JavaVersion since) {
        return javaModules.values().stream()
                .filter(matchesSince(since))
                .collect(Collectors.toList());
    }

    private Collection<JavaModule> getJavaModules() {
        return javaModules.values();
    }

    private Collection<JavaPackage> getJavaPackages() {
        return javaModules.values().stream()
                .flatMap(m -> m.getJavaPackages().stream())
                .sorted(Comparator.comparing(JavaPackage::getName))
                .collect(Collectors.toList());
    }

    private Map<JavaVersion, List<JavaModule>> getModulesPerVersion() {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = new TreeMap<>(comparator);

        for (JavaVersion javaVersion : javaVersions) {
            if (javaVersion.hasModules()) {
                List<JavaModule> javaModules = getJavaModules(javaVersion).stream()
                        .sorted(Comparator.comparing(JavaModule::getName))
                        .collect(Collectors.toList());
                if (!javaModules.isEmpty()) {
                    modulesPerVersion.put(javaVersion, javaModules);
                }
            }
        }
        return modulesPerVersion;
    }

    private Map<JavaVersion, List<JavaPackage>> getPackagesPerVersion() {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        for (JavaVersion javaVersion : javaVersions) {
            if (!javaVersion.hasModules()) {
                List<JavaPackage> javaPackages = getJavaModules(javaVersion).stream()
                        .flatMap(m -> m.getJavaPackages(javaVersion).stream())
                        .sorted(Comparator.comparing(JavaPackage::getName))
                        .collect(Collectors.toList());
                if (!javaPackages.isEmpty()) {
                    packagesPerVersion.put(javaVersion, javaPackages);
                }
            }
        }
        return packagesPerVersion;
    }

    public static Map<JavaVersion, List<JavaModule>> getNewModulesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {

        Iterator<JavaAPI> iterator = javaAPIs.descendingMap().values().iterator();
        JavaAPI latestJavaAPI = iterator.next();

        JavaAPI javaAPI = latestJavaAPI.copy();
        while (iterator.hasNext()) {
            javaAPI.merge(iterator.next().copy());
        }
        javaAPI.retainSince(minimalJavaVersion);

        Map<JavaVersion, List<JavaModule>> result = javaAPI.getModulesPerVersion();
        Map<JavaVersion, List<JavaModule>> modulesPerVersionFromAllAPIs = getNewModulesPerVersion(javaAPIs);

        for (Map.Entry<JavaVersion, List<JavaModule>> entry : result.entrySet()) {
            JavaVersion javaVersion = entry.getKey();
            List<JavaModule> javaModulesFromLatestAPI = entry.getValue();
            List<JavaModule> javaModulesFromAllAPIs = modulesPerVersionFromAllAPIs.remove(javaVersion);
            if (javaModulesFromAllAPIs != null) {
                JavaModule.mergeAll(javaModulesFromLatestAPI, javaModulesFromAllAPIs);
            }
        }

        result.putAll(modulesPerVersionFromAllAPIs);

        return result;
    }

    public static Map<JavaVersion, List<JavaPackage>> getNewPackagesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {

        Iterator<JavaAPI> iterator = javaAPIs.descendingMap().values().iterator();
        JavaAPI latestJavaAPI = iterator.next();

        JavaAPI javaAPI = latestJavaAPI.copy();
        while (iterator.hasNext()) {
            javaAPI.merge(iterator.next().copy());
        }
        javaAPI.retainSince(minimalJavaVersion);

        Map<JavaVersion, List<JavaPackage>> result = javaAPI.getPackagesPerVersion();
        Map<JavaVersion, List<JavaPackage>> packagesPerVersionFromAllAPIs = getNewPackagesPerVersion(javaAPIs);

        for (Map.Entry<JavaVersion, List<JavaPackage>> entry : result.entrySet()) {
            JavaVersion javaVersion = entry.getKey();
            List<JavaPackage> javaPackagesFromLatestAPI = entry.getValue();
            List<JavaPackage> javaPackagesFromAllAPIs = packagesPerVersionFromAllAPIs.remove(javaVersion);
            if (javaPackagesFromAllAPIs != null) {
                JavaPackage.mergeAll(javaPackagesFromLatestAPI, javaPackagesFromAllAPIs);
            }
        }

        result.putAll(packagesPerVersionFromAllAPIs);

        return result;
    }

    private static Map<JavaVersion, List<JavaModule>> getNewModulesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            if (!current.getValue().javaModules.containsKey(null)) {
                JavaAPI javaAPI = new JavaAPI(current.getValue().getJavadoc());
                if (previous.getValue().javaModules.containsKey(null)) {
                    // current uses modules but previous does not, use collectNewPackages
                    collectNewPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                } else {
                    // both current and previous use modules, use collectNewModules
                    collectNewModules(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                }
                List<JavaModule> javaModules = javaAPI.javaModules.values().stream()
                        .sorted(Comparator.comparing(JavaModule::getName))
                        .collect(Collectors.toList());
                if (!javaModules.isEmpty()) {
                    modulesPerVersion.put(current.getKey(), javaModules);
                }
            }
            // else current does not contain actual modules - use getNewPackagesPerVersion
            current = previous;
        }
        return modulesPerVersion;
    }

    private static Map<JavaVersion, List<JavaPackage>> getNewPackagesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            if (current.getValue().javaModules.containsKey(null)) {
                JavaAPI javaAPI = new JavaAPI(current.getValue().getJavadoc());
                collectNewPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                List<JavaPackage> javaPackages = javaAPI.javaModules.values().stream()
                        .flatMap(m -> m.getJavaPackages().stream())
                        .sorted(Comparator.comparing(JavaPackage::getName))
                        .collect(Collectors.toList());
                if (!javaPackages.isEmpty()) {
                    packagesPerVersion.put(current.getKey(), javaPackages);
                }
            }
            // else current contains actual modules - use getNewModulesPerVersion
            current = previous;
        }
        return packagesPerVersion;
    }

    private static void collectNewModules(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaModule currentModule : current.getJavaModules()) {
            JavaModule previousModule = previous.findJavaModule(currentModule.getName());
            if (previousModule == null) {
                // the entire module is new
                dest.addModule(currentModule.getName(), since, currentModule.isDeprecated());
            } else {
                // check packages
                collectNewPackages(currentModule, previousModule, since, dest);
            }
        }
    }

    private static void collectNewPackages(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaPackage currentPackage : current.getJavaPackages()) {
            JavaPackage previousPackage = previous.findJavaPackage(currentPackage.getName());
            if (previousPackage == null) {
                // the entire package is new
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackage(moduleName, currentPackage.getName(), since, currentPackage.isDeprecated());
            } else {
                // check classes
                collectNewClasses(currentPackage, previousPackage, since, dest);
            }
        }
    }

    private static void collectNewPackages(JavaModule currentModule, JavaModule previousModule, JavaVersion since, JavaAPI dest) {
        for (JavaPackage currentPackage : currentModule.getJavaPackages()) {
            JavaPackage previousPackage = previousModule.findJavaPackage(currentPackage.getName());
            if (previousPackage == null) {
                // the entire package was added
                String moduleName = currentModule.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackage(moduleName, currentPackage.getName(), since, currentPackage.isDeprecated());
            } else {
                // check classes
                collectNewClasses(currentPackage, previousPackage, since, dest);
            }
        }
    }

    private static void collectNewClasses(JavaPackage currentPackage, JavaPackage previousPackage, JavaVersion since, JavaAPI dest) {
        for (JavaClass currentClass : currentPackage.getJavaClasses()) {
            JavaClass previousClass = previousPackage.findJavaClass(currentClass.getName());
            if (previousClass == null) {
                // the entire class is new
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();
                String packageName = currentPackage.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                dest.addClass(moduleName, packageName, currentClass.getName(), since, true, currentClass.getInheritedMethodSignatures());
            } else {
                // check members
                collectNewMembers(currentPackage, currentClass, previousClass, since, dest);
            }
        }
    }

    private static void collectNewMembers(JavaPackage currentPackage, JavaClass currentClass, JavaClass previousClass, JavaVersion since, JavaAPI dest) {
        for (JavaMember currentMember : currentClass.getJavaMembers()) {
            JavaMember previousMember = previousClass.findJavaMember(currentMember.getType(), currentMember.getOriginalSignature());
            if (previousMember == null && (currentMember.getType() != JavaMember.Type.METHOD || !previousClass.isInheritedMethod(currentMember.getOriginalSignature()))) {
                 // the member was added
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();
                String packageName = currentPackage.getName();
                String className = currentClass.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                dest.addClassIfNotExists(moduleName, packageName, className, null, currentClass.isDeprecated(), currentClass.getInheritedMethodSignatures());
                dest.addMember(moduleName, packageName, className, currentMember.getType(), currentMember.getOriginalSignature(), since, currentMember.isDeprecated());
            }
            // else currentMember already existed
        }
    }

    public static Map<JavaVersion, List<JavaModule>> getDeprecatedModulesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            if (!current.getValue().javaModules.containsKey(null)) {
                JavaAPI javaAPI = new JavaAPI(current.getValue().getJavadoc());
                if (previous.getValue().javaModules.containsKey(null)) {
                    // current uses modules but previous does not, use collectDeprecatedPackages
                    collectDeprecatedPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                } else {
                    // both current and previous use modules, use collectDeprecatedModules
                    collectDeprecatedModules(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                }
                List<JavaModule> javaModules = javaAPI.javaModules.values().stream()
                        .sorted(Comparator.comparing(JavaModule::getName))
                        .collect(Collectors.toList());
                if (!javaModules.isEmpty()) {
                    modulesPerVersion.put(current.getKey(), javaModules);
                }
            }
            // else current does not contain actual modules - use getDeprecatedPackagesPerVersion
            current = previous;
        }
        return modulesPerVersion;
    }

    public static Map<JavaVersion, List<JavaPackage>> getDeprecatedPackagesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            if (current.getValue().javaModules.containsKey(null)) {
                JavaAPI javaAPI = new JavaAPI(current.getValue().getJavadoc());
                collectDeprecatedPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                List<JavaPackage> javaPackages = javaAPI.javaModules.values().stream()
                        .flatMap(m -> m.getJavaPackages().stream())
                        .sorted(Comparator.comparing(JavaPackage::getName))
                        .collect(Collectors.toList());
                if (!javaPackages.isEmpty()) {
                    packagesPerVersion.put(current.getKey(), javaPackages);
                }
            }
            // else current contains actual modules - use getDeprecatedModulesPerVersion
            current = previous;
        }
        return packagesPerVersion;
    }

    private static void collectDeprecatedModules(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaModule currentModule : current.getJavaModules()) {
            JavaModule previousModule = previous.findJavaModule(currentModule.getName());
            if (previousModule != null) {
                if (currentModule.isDeprecated() && !previousModule.isDeprecated()) {
                    // the entire module became deprecated
                    dest.addModule(currentModule.getName(), since, true);
                } else {
                    // either the module's deprecation status has not changed, or it became non-deprecated; check packages
                    collectDeprecatedPackages(currentModule, previousModule, since, dest);
                }
            }
            // else currentModule is new
        }
    }

    private static void collectDeprecatedPackages(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaPackage currentPackage : current.getJavaPackages()) {
            JavaPackage previousPackage = previous.findJavaPackage(currentPackage.getName());
            if (previousPackage != null) {
                if (currentPackage.isDeprecated() && !previousPackage.isDeprecated()) {
                    // the entire package became deprecated
                    JavaModule currentModule = currentPackage.getJavaModule();
                    String moduleName = currentModule.getName();

                    dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                    dest.addPackage(moduleName, currentPackage.getName(), since, true);
                } else {
                    // either the package's deprecation status has not changed, or it became non-deprecated; check classes
                    collectDeprecatedClasses(currentPackage, previousPackage, since, dest);
                }
            }
            // else currentPackage is new
        }
    }

    private static void collectDeprecatedPackages(JavaModule currentModule, JavaModule previousModule, JavaVersion since, JavaAPI dest) {
        for (JavaPackage currentPackage : currentModule.getJavaPackages()) {
            JavaPackage previousPackage = previousModule.findJavaPackage(currentPackage.getName());
            if (previousPackage != null) {
                if (currentPackage.isDeprecated() && !previousPackage.isDeprecated()) {
                    // the entire package became deprecated
                    String moduleName = currentModule.getName();

                    dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                    dest.addPackage(moduleName, currentPackage.getName(), since, true);
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
                    JavaModule currentModule = currentPackage.getJavaModule();
                    String moduleName = currentModule.getName();
                    String packageName = currentPackage.getName();

                    dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                    dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                    dest.addClass(moduleName, packageName, currentClass.getName(), since, true, currentClass.getInheritedMethodSignatures());
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
            JavaMember previousMember = previousClass.findJavaMember(currentMember.getType(), currentMember.getOriginalSignature());
            if (previousMember != null && currentMember.isDeprecated() && !previousMember.isDeprecated()) {
                // the member became deprecated
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();
                String packageName = currentPackage.getName();
                String className = currentClass.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                dest.addClassIfNotExists(moduleName, packageName, className, null, currentClass.isDeprecated(), currentClass.getInheritedMethodSignatures());
                dest.addMember(moduleName, packageName, className, currentMember.getType(), currentMember.getOriginalSignature(), since, true);
            } else if (currentMember.isDeprecated() && currentMember.getType() == JavaMember.Type.METHOD && previousClass.isInheritedMethod(currentMember.getOriginalSignature())) {
                // the member was inherited before but now became deprecated
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();
                String packageName = currentPackage.getName();
                String className = currentClass.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                dest.addClassIfNotExists(moduleName, packageName, className, null, currentClass.isDeprecated(), currentClass.getInheritedMethodSignatures());
                dest.addMember(moduleName, packageName, className, currentMember.getType(), currentMember.getOriginalSignature(), since, true);
            }
            // else currentMember is new, or the member's deprecation status has not change, or it became non-deprecated
        }
    }

    public static Map<JavaVersion, List<JavaModule>> getRemovedModulesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            if (!current.getValue().javaModules.containsKey(null)) {
                JavaAPI javaAPI = new JavaAPI(previous.getValue().getJavadoc());
                if (previous.getValue().javaModules.containsKey(null)) {
                    // current uses modules but previous does not, use collectRemovedPackages
                    collectRemovedPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                } else {
                    // both current and previous use modules, use collectRemovedModules
                    collectRemovedModules(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                }
                List<JavaModule> javaModules = javaAPI.javaModules.values().stream()
                        .sorted(Comparator.comparing(JavaModule::getName))
                        .collect(Collectors.toList());
                if (!javaModules.isEmpty()) {
                    modulesPerVersion.put(current.getKey(), javaModules);
                }
            }
            // else current does not contain actual modules - use getRemovedPackagesPerVersion
            current = previous;
        }
        return modulesPerVersion;
    }

    public static Map<JavaVersion, List<JavaPackage>> getRemovedPackagesPerVersion(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Comparator<JavaVersion> comparator = Comparator.reverseOrder();
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = new TreeMap<>(comparator);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            if (current.getValue().javaModules.containsKey(null)) {
                JavaAPI javaAPI = new JavaAPI(previous.getValue().getJavadoc());
                collectRemovedPackages(current.getValue(), previous.getValue(), current.getKey(), javaAPI);
                List<JavaPackage> javaPackages = javaAPI.javaModules.values().stream()
                        .flatMap(m -> m.getJavaPackages().stream())
                        .sorted(Comparator.comparing(JavaPackage::getName))
                        .collect(Collectors.toList());
                if (!javaPackages.isEmpty()) {
                    packagesPerVersion.put(current.getKey(), javaPackages);
                }
            }
            // else current contains actual modules - use getRemovedModulesPerVersion
            current = previous;
        }
        return packagesPerVersion;
    }

    private static void collectRemovedModules(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaModule previousModule : previous.getJavaModules()) {
            JavaModule currentModule = current.findJavaModule(previousModule.getName());
            if (currentModule == null) {
                // the entire module was removed
                dest.addModule(previousModule.getName(), since, previousModule.isDeprecated());
            } else {
                // check packages
                collectRemovedPackages(currentModule, previousModule, since, dest);
            }
        }
    }

    private static void collectRemovedPackages(JavaAPI current, JavaAPI previous, JavaVersion since, JavaAPI dest) {
        for (JavaPackage previousPackage : previous.getJavaPackages()) {
            JavaPackage currentPackage = current.findJavaPackage(previousPackage.getName());
            if (currentPackage == null) {
                // the entire package was removed
                JavaModule previousModule = previousPackage.getJavaModule();
                String moduleName = previousModule.getName();

                dest.addModuleIfNotExists(moduleName, null, previousModule.isDeprecated());
                dest.addPackage(moduleName, previousPackage.getName(), since, previousPackage.isDeprecated());
            } else {
                // check classes
                collectRemovedClasses(currentPackage, previousPackage, since, dest);
            }
        }
    }

    private static void collectRemovedPackages(JavaModule currentModule, JavaModule previousModule, JavaVersion since, JavaAPI dest) {
        for (JavaPackage previousPackage : previousModule.getJavaPackages()) {
            JavaPackage currentPackage = currentModule.findJavaPackage(previousPackage.getName());
            if (currentPackage == null) {
                // the entire package was removed
                String moduleName = previousModule.getName();

                dest.addModuleIfNotExists(moduleName, null, previousModule.isDeprecated());
                dest.addPackage(moduleName, previousPackage.getName(), since, previousPackage.isDeprecated());
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
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();
                String packageName = currentPackage.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                dest.addClass(moduleName, packageName, previousClass.getName(), since, previousClass.isDeprecated(), previousClass.getInheritedMethodSignatures());
            } else {
                // check members
                collectRemovedMembers(currentPackage, currentClass, previousClass, since, dest);
            }
        }
    }

    private static void collectRemovedMembers(JavaPackage currentPackage, JavaClass currentClass, JavaClass previousClass, JavaVersion since, JavaAPI dest) {
        for (JavaMember previousMember : previousClass.getJavaMembers()) {
            JavaMember currentMember = currentClass.findJavaMember(previousMember.getType(), previousMember.getOriginalSignature());
            if (currentMember == null && (previousMember.getType() != JavaMember.Type.METHOD || !currentClass.isInheritedMethod(previousMember.getOriginalSignature()))) {
                // the member was removed
                JavaModule currentModule = currentPackage.getJavaModule();
                String moduleName = currentModule.getName();
                String packageName = currentPackage.getName();
                String className = currentClass.getName();

                dest.addModuleIfNotExists(moduleName, null, currentModule.isDeprecated());
                dest.addPackageIfNotExists(moduleName, packageName, null, currentPackage.isDeprecated());
                dest.addClassIfNotExists(moduleName, packageName, className, null, currentClass.isDeprecated(), currentClass.getInheritedMethodSignatures());
                dest.addMember(moduleName, packageName, className, previousMember.getType(), previousMember.getOriginalSignature(), since, previousMember.isDeprecated());
            }
            // else currentMember still exists
        }
    }
}
