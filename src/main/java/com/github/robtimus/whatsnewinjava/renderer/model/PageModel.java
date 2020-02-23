package com.github.robtimus.whatsnewinjava.renderer.model;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;
import com.github.robtimus.whatsnewinjava.parser.model.JavaInterfaceList;
import com.github.robtimus.whatsnewinjava.parser.model.JavaMember;
import com.github.robtimus.whatsnewinjava.parser.model.JavaModule;
import com.github.robtimus.whatsnewinjava.parser.model.JavaPackage;
import com.github.robtimus.whatsnewinjava.parser.model.JavaVersion;

public final class PageModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageModel.class);

    private final Map<JavaVersion, Map<String, PageModule>> modulesPerVersion;
    private final Map<JavaVersion, Map<String, PagePackage>> packagesPerVersion;

    private PageModel() {
        modulesPerVersion = new TreeMap<>(reverseOrder());
        packagesPerVersion = new TreeMap<>(reverseOrder());
    }

    public Map<JavaVersion, Collection<PageModule>> getModulesPerVersion() {
        Map<JavaVersion, Collection<PageModule>> result = new LinkedHashMap<>(modulesPerVersion.size());
        for (Map.Entry<JavaVersion, Map<String, PageModule>> entry : modulesPerVersion.entrySet()) {
            result.put(entry.getKey(), entry.getValue().values());
        }
        return result;
    }

    public Map<JavaVersion, Collection<PagePackage>> getPackagesPerVersion() {
        Map<JavaVersion, Collection<PagePackage>> result = new LinkedHashMap<>(packagesPerVersion.size());
        for (Map.Entry<JavaVersion, Map<String, PagePackage>> entry : packagesPerVersion.entrySet()) {
            result.put(entry.getKey(), entry.getValue().values());
        }
        return result;
    }

    private PageModule ensureModuleExists(JavaVersion version, String moduleName) {
        Map<String, PageModule> modules = modulesPerVersion.computeIfAbsent(version, k -> new TreeMap<>());
        return modules.computeIfAbsent(moduleName, k -> new PageModule(moduleName));
    }

    private PagePackage ensurePackageExists(JavaVersion version, String packageName) {
        Map<String, PagePackage> packages = packagesPerVersion.computeIfAbsent(version, k -> new TreeMap<>());
        return packages.computeIfAbsent(packageName, k -> new PagePackage(null, packageName));
    }

    private PagePackage ensurePackageExists(JavaVersion version, String moduleName, String packageName) {
        if (version.hasModules()) {
            return ensureModuleExists(version, moduleName)
                    .ensurePackageExists(packageName);
        }
        return ensurePackageExists(version, packageName);
    }

    public static PageModel forNew(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {
        PageModel pageModel = new PageModel();
        SinceHelper sinceHelper = new SinceHelper(javaAPIs, minimalJavaVersion);

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            JavaAPI currentAPI = current.getValue();
            JavaAPI previousAPI = previous.getValue();
            JavaVersion version = current.getKey();
            // collect based on @since
            if (currentAPI.hasAutomaticModule()) {
                collectPackagesForNew(currentAPI, minimalJavaVersion, pageModel, sinceHelper);
            } else {
                collectModulesForNew(currentAPI, minimalJavaVersion, pageModel, sinceHelper);
            }
            // collect based on differences
            if (currentAPI.hasAutomaticModule() || previousAPI.hasAutomaticModule()) {
                // current, previous or both don't use modules, use collectPackagesForRemoved
                collectPackagesForNew(currentAPI, previousAPI, minimalJavaVersion, version, pageModel, sinceHelper);
            } else {
                // both current and previous use modules, use collectModulesForRemoved
                collectModulesForNew(currentAPI, previousAPI, minimalJavaVersion, version, pageModel, sinceHelper);
            }
            if (version.introducedModules()) {
                // all packages in modules may be reported as new, in which case the entire module should be reported as new
                flattenModulesWithAllNewPackages(currentAPI, previousAPI, version, pageModel);
            }

            current = previous;
        }
        return pageModel;
    }

    private static void collectPackagesForNew(JavaAPI currentAPI, JavaVersion minimalJavaVersion, PageModel pageModel, SinceHelper sinceHelper) {
        for (JavaPackage currentPackage : currentAPI.getJavaPackages()) {
            String packageName = currentPackage.getName();
            JavaVersion since = sinceHelper.getSince(currentPackage);
            if (isMinimalJavaVersion(since, minimalJavaVersion)) {
                // the package should be included for its own version
                pageModel.ensurePackageExists(since, currentPackage.getJavaModule().getName(), packageName);
            }
            // collect classes and members as well, except if they share the package's version
            collectClassesForNew(currentPackage, minimalJavaVersion, since, sinceHelper, v -> pageModel.ensurePackageExists(v, currentPackage.getJavaModule().getName(), packageName));
        }
    }

    private static void collectModulesForNew(JavaAPI currentAPI, JavaVersion minimalJavaVersion, PageModel pageModel, SinceHelper sinceHelper) {
        for (JavaModule currentModule : currentAPI.getJavaModules()) {
            String moduleName = currentModule.getName();
            JavaVersion since = sinceHelper.getSince(currentModule);
            // only include the module if !since.introducedModules() (so since > 9), and if it has only packages that were added in the same version
            // currentModule.isSince(since) will return false if since.introducedModules() or any package has a different since
            if (isMinimalJavaVersion(since, minimalJavaVersion) && currentModule.isSince(since)) {
                // the module should be included for its own version
                pageModel.ensureModuleExists(since, moduleName);
            }
            // collect packages, classes and members as well, except if they share the module's version
            collectPackagesForNew(currentModule, minimalJavaVersion, since, pageModel, sinceHelper);
        }
    }

    private static void collectPackagesForNew(JavaModule currentModule, JavaVersion minimalJavaVersion, JavaVersion omitForVersion, PageModel pageModel, SinceHelper sinceHelper) {
        for (JavaPackage currentPackage : currentModule.getJavaPackages()) {
            String packageName = currentPackage.getName();
            JavaVersion since = sinceHelper.getSince(currentPackage);
            if (isMinimalJavaVersion(since, minimalJavaVersion) && !since.equals(omitForVersion)) {
                // the package should be included for its own version
                pageModel.ensurePackageExists(since, currentModule.getName(), packageName);
            }
            // collect classes and members as well, except if they share the package's version
            collectClassesForNew(currentPackage, minimalJavaVersion, since, sinceHelper, v -> pageModel.ensurePackageExists(v, currentModule.getName(), packageName));
        }
    }

    private static void collectClassesForNew(JavaPackage currentPackage, JavaVersion minimalJavaVersion, JavaVersion omitForVersion, SinceHelper sinceHelper, Function<JavaVersion, PagePackage> existingPackage) {
        for (JavaClass currentClass : currentPackage.getJavaClasses()) {
            String className = currentClass.getName();
            JavaVersion since = sinceHelper.getSince(currentClass);
            if (isMinimalJavaVersion(since, minimalJavaVersion) && !since.equals(omitForVersion)) {
                // the class should be included for its own version
                existingPackage.apply(since)
                        .ensureClassExists(className, currentClass.getType(), currentClass.getSuperClass());
            }
            // collect members as well, except if they share the class's version
            collectMembersForNew(currentClass, minimalJavaVersion, since, sinceHelper, existingPackage);
        }
    }

    private static void collectMembersForNew(JavaClass currentClass, JavaVersion minimalJavaVersion, JavaVersion omitForVersion, SinceHelper sinceHelper, Function<JavaVersion, PagePackage> existingPackage) {
        for (JavaMember currentMember : currentClass.getJavaMembers()) {
            JavaVersion since = sinceHelper.getSince(currentMember);
            if (isMinimalJavaVersion(since, minimalJavaVersion) && !since.equals(omitForVersion)) {
                // the member should be included for its own version
                existingPackage.apply(since)
                        .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                        .addMember(currentMember.getType(), currentMember.getPrettifiedSignature());
            }
        }
    }

    private static void collectPackagesForNew(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion minimalJavaVersion, JavaVersion version, PageModel pageModel, SinceHelper sinceHelper) {
        for (JavaPackage currentPackage : currentAPI.getJavaPackages()) {
            String packageName = currentPackage.getName();
            JavaPackage previousPackage = previousAPI.findJavaPackage(packageName);
            if (previousPackage == null) {
                // the entire package is new
                JavaVersion since = sinceHelper.getSince(currentPackage);
                if (since == null || isMinimalJavaVersion(since, minimalJavaVersion)) {
                    // the package does not have an @since that's too old
                    pageModel.ensurePackageExists(version, currentPackage.getJavaModule().getName(), packageName);
                }
            } else {
                // the package is not new; check classes
                collectClassesForNew(currentPackage, previousPackage, minimalJavaVersion, sinceHelper, () -> pageModel.ensurePackageExists(version, currentPackage.getJavaModule().getName(), packageName));
            }
        }
    }

    private static void collectModulesForNew(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion minimalJavaVersion, JavaVersion version, PageModel pageModel, SinceHelper sinceHelper) {
        for (JavaModule currentModule : currentAPI.getJavaModules()) {
            String moduleName = currentModule.getName();
            JavaModule previousModule = previousAPI.findJavaModule(moduleName);
            if (previousModule == null) {
                // the entire module is new
                JavaVersion since = sinceHelper.getSince(currentModule);
                if (since == null || isMinimalJavaVersion(since, minimalJavaVersion)) {
                    // the module does not have an @since that's too old
                    pageModel.ensureModuleExists(version, moduleName);
                }
            } else {
                // the module is not new; check packages
                collectPackagesForNew(currentModule, previousModule, minimalJavaVersion, version, pageModel, sinceHelper);
            }
        }
    }

    private static void collectPackagesForNew(JavaModule currentModule, JavaModule previousModule, JavaVersion minimalJavaVersion, JavaVersion version, PageModel pageModel, SinceHelper sinceHelper) {
        for (JavaPackage currentPackage : currentModule.getJavaPackages()) {
            String packageName = currentPackage.getName();
            JavaPackage previousPackage = previousModule.findJavaPackage(packageName);
            if (previousPackage == null) {
                // the entire package was added
                JavaVersion since = sinceHelper.getSince(currentPackage);
                if (since == null || isMinimalJavaVersion(since, minimalJavaVersion)) {
                    // the package does not have an @since that's too old
                    pageModel.ensureModuleExists(version, currentModule.getName())
                            .ensurePackageExists(packageName);
                }
            } else {
                // the package is not new; check classes
                collectClassesForNew(currentPackage, previousPackage, minimalJavaVersion, sinceHelper, () -> pageModel.ensureModuleExists(version, currentModule.getName())
                        .ensurePackageExists(packageName));
            }
        }
    }

    private static void collectClassesForNew(JavaPackage currentPackage, JavaPackage previousPackage, JavaVersion minimalJavaVersion, SinceHelper sinceHelper, Supplier<PagePackage> existingPackage) {
        for (JavaClass currentClass : currentPackage.getJavaClasses()) {
            String className = currentClass.getName();
            JavaClass previousClass = previousPackage.findJavaClass(className);
            if (previousClass == null) {
                // the entire class is new
                JavaVersion since = sinceHelper.getSince(currentClass);
                if (since == null || isMinimalJavaVersion(since, minimalJavaVersion)) {
                    // the class does not have an @since that's too old
                    existingPackage.get()
                            .ensureClassExists(className, currentClass.getType(), currentClass.getSuperClass());
                }
            } else {
                // the class is not new
                // check super class
                //collectSuperClassForNew(currentClass, previousClass, existingPackage);
                // check interfaces
                //collectInterfacesForNew(currentClass, previousClass, existingPackage);
                // check members
                collectMembersForNew(currentClass, previousClass, minimalJavaVersion, sinceHelper, existingPackage);
            }
        }
    }

    private static void collectSuperClassForNew(JavaClass currentClass, JavaClass previousClass, Supplier<PagePackage> existingPackage) {
        if (!Objects.equals(currentClass.getSuperClass(), previousClass.getSuperClass())) {
            existingPackage.get()
                    .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                    .setPreviousSuperClass(previousClass.getSuperClass());
        }
    }

    private static void collectInterfacesForNew(JavaClass currentClass, JavaClass previousClass, Supplier<PagePackage> existingPackage) {
        JavaInterfaceList previousInterfaceList = previousClass.getInterfaceList();
        for (String interfaceName : currentClass.getInterfaceList().getInterfaceNames()) {
            if (previousInterfaceList.hasInterfaceName(interfaceName)) {
                String previousInterfaceName = previousInterfaceList.getGenericInterfaceName(interfaceName);
                if (!interfaceName.equals(previousInterfaceName)) {
                    existingPackage.get()
                            .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                            .addAlteredInterface(interfaceName, previousInterfaceName);
                }
                // else the interface is identical
            } else {
                existingPackage.get()
                        .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                        .addInterface(interfaceName);
            }
        }
    }

    private static void collectMembersForNew(JavaClass currentClass, JavaClass previousClass, JavaVersion minimalJavaVersion, SinceHelper sinceHelper, Supplier<PagePackage> existingPackage) {
        for (JavaMember currentMember : currentClass.getJavaMembers()) {
            JavaMember.Type type = currentMember.getType();
            String originalSignature = currentMember.getOriginalSignature();
            JavaMember previousMember = previousClass.findJavaMember(type, originalSignature);
            if (previousMember == null && !isInheritedMethod(previousClass, type, originalSignature)) {
                // the method is new, and not just overridden
                JavaVersion since = sinceHelper.getSince(currentMember);
                if (since == null || isMinimalJavaVersion(since, minimalJavaVersion)) {
                    // the method does not have an @since that's too old
                    existingPackage.get()
                            .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                            .addMember(type, currentMember.getPrettifiedSignature());
                }
            }
        }
    }

    private static void flattenModulesWithAllNewPackages(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion version, PageModel pageModel) {
        Map<String, PageModule> pageModules = pageModel.modulesPerVersion.get(version);
        List<PageModule> pageModulesToReplace = new ArrayList<>();
        for (PageModule pageModule : pageModules.values()) {
            if (allPackagesAreNew(pageModule, currentAPI, previousAPI, version)) {
                pageModulesToReplace.add(pageModule);
            }
        }
        for (PageModule pageModule : pageModulesToReplace) {
            String moduleName = pageModule.getName();
            pageModules.put(moduleName, new PageModule(moduleName));
        }
    }

    private static boolean allPackagesAreNew(PageModule pageModule, JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion version) {
        for (PagePackage pagePackage : pageModule.getPackages()) {
            String packageName = pagePackage.getName();
            JavaPackage javaPackage = currentAPI.findJavaPackage(packageName);
            if (javaPackage.getSince() != null && !javaPackage.isAtLeastSince(version)) {
                // the package has a lower since so it's not new
                return false;
            }
            if (!pagePackage.getClasses().isEmpty()) {
                // the package contains content which needs to be reported separately
                return false;
            }
            if (previousAPI.findJavaPackage(packageName) != null) {
                // the package already existed
                return false;
            }
        }
        return true;
    }

    private static boolean isMinimalJavaVersion(JavaVersion since, JavaVersion minimalJavaVersion) {
        return since != null && since.compareTo(minimalJavaVersion) >= 0;
    }

    public static PageModel forDeprecated(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        PageModel pageModel = new PageModel();

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            JavaAPI currentAPI = current.getValue();
            JavaAPI previousAPI = previous.getValue();
            JavaVersion version = current.getKey();
            if (currentAPI.hasAutomaticModule() || previousAPI.hasAutomaticModule()) {
                // current, previous or both don't use modules, use collectPackagesForRemoved
                collectPackagesForDeprecated(currentAPI, previousAPI, version, pageModel);
            } else {
                // both current and previous use modules, use collectModulesForRemoved
                collectModulesForDeprecated(currentAPI, previousAPI, version, pageModel);
            }
            current = previous;
        }

        return pageModel;
    }

    private static void collectPackagesForDeprecated(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion version, PageModel pageModel) {
        for (JavaPackage currentPackage : currentAPI.getJavaPackages()) {
            String packageName = currentPackage.getName();
            JavaPackage previousPackage = previousAPI.findJavaPackage(packageName);
            if (previousPackage != null) {
                if (currentPackage.isDeprecated() && !previousPackage.isDeprecated()) {
                    // the entire package became deprecated
                    JavaModule currentModule = currentPackage.getJavaModule();
                    if (currentModule.isAutomatic()) {
                        pageModel.ensurePackageExists(version, packageName);
                    } else {
                        pageModel.ensureModuleExists(version, currentModule.getName())
                                .ensurePackageExists(packageName);
                    }
                } else {
                    // the package is either not deprecated or it already was
                    Supplier<PagePackage> existingPackage;
                    JavaModule currentModule = currentPackage.getJavaModule();
                    if (currentModule.isAutomatic()) {
                        existingPackage = () -> pageModel.ensurePackageExists(version, packageName);
                    } else {
                        existingPackage = () -> pageModel.ensureModuleExists(version, currentModule.getName())
                                .ensurePackageExists(packageName);
                    }
                    // check classes
                    collectClassesForDeprecated(currentPackage, previousPackage, existingPackage);
                }
            }
        }
    }

    private static void collectModulesForDeprecated(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion version, PageModel pageModel) {
        for (JavaModule currentModule : currentAPI.getJavaModules()) {
            String moduleName = currentModule.getName();
            JavaModule previousModule = previousAPI.findJavaModule(moduleName);
            if (previousModule != null) {
                if (currentModule.isDeprecated() && !previousModule.isDeprecated()) {
                    // the entire module became deprecated
                    pageModel.ensureModuleExists(version, moduleName);
                } else {
                    // the module is either not deprecated or it already was; check packages
                    collectPackagesForDeprecated(currentModule, previousModule, version, pageModel);
                }
            }
        }
    }

    private static void collectPackagesForDeprecated(JavaModule currentModule, JavaModule previousModule, JavaVersion version, PageModel pageModel) {
        for (JavaPackage currentPackage : currentModule.getJavaPackages()) {
            String packageName = currentPackage.getName();
            JavaPackage previousPackage = previousModule.findJavaPackage(packageName);
            if (previousPackage != null) {
                if (currentPackage.isDeprecated() && !previousPackage.isDeprecated()) {
                    // the entire package became deprecated
                    pageModel.ensureModuleExists(version, currentModule.getName())
                            .ensurePackageExists(packageName);
                } else {
                    // the package is either not deprecated or it already was; check classes
                    collectClassesForDeprecated(currentPackage, previousPackage, () -> pageModel.ensureModuleExists(version, currentModule.getName())
                            .ensurePackageExists(packageName));
                }
            }
        }
    }

    private static void collectClassesForDeprecated(JavaPackage currentPackage, JavaPackage previousPackage, Supplier<PagePackage> existingPackage) {
        for (JavaClass currentClass : currentPackage.getJavaClasses()) {
            String className = currentClass.getName();
            JavaClass previousClass = previousPackage.findJavaClass(className);
            if (previousClass != null) {
                if (currentClass.isDeprecated() && !previousClass.isDeprecated()) {
                    // the entire class became deprecated
                    existingPackage.get()
                            .ensureClassExists(className, currentClass.getType(), currentClass.getSuperClass());
                } else {
                    // the class is either not deprecated or it already was; check members
                    collectMembersForDeprecated(currentClass, previousClass, existingPackage);
                }
            }
        }
    }

    private static void collectMembersForDeprecated(JavaClass currentClass, JavaClass previousClass, Supplier<PagePackage> existingPackage) {
        for (JavaMember currentMember : currentClass.getJavaMembers()) {
            JavaMember.Type type = currentMember.getType();
            String originalSignature = currentMember.getOriginalSignature();
            JavaMember previousMember = previousClass.findJavaMember(type, originalSignature);
            if (currentMember.isDeprecated() && (
                    memberWasNotDeprecated(previousMember)
                    || memberWasInherited(currentMember, previousClass)
                    || memberIsNew(currentMember, previousMember, previousClass))) {

                existingPackage.get()
                        .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                        .addMember(type, currentMember.getPrettifiedSignature());
            }
            // else the member is not deprecated, or it already was
        }
    }

    private static boolean memberWasNotDeprecated(JavaMember previousMember) {
        // the member became deprecated
        return previousMember != null && !previousMember.isDeprecated();
    }

    private static boolean memberWasInherited(JavaMember currentMember, JavaClass previousClass) {
        // the member was inherited before but now became deprecated
        return isInheritedMethod(previousClass, currentMember);
    }

    private static boolean memberIsNew(JavaMember currentMember, JavaMember previousMember, JavaClass previousClass) {
        // the member is new but immediately became deprecated (e.g. Java 13's String.formatted)
        return previousMember == null && !isInheritedMethod(previousClass, currentMember);
    }

    public static PageModel forRemoved(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        PageModel pageModel = new PageModel();

        Iterator<Map.Entry<JavaVersion, JavaAPI>> iterator = javaAPIs.descendingMap().entrySet().iterator();
        Map.Entry<JavaVersion, JavaAPI> current = iterator.next();
        while (iterator.hasNext()) {
            Map.Entry<JavaVersion, JavaAPI> previous = iterator.next();
            JavaAPI currentAPI = current.getValue();
            JavaAPI previousAPI = previous.getValue();
            JavaVersion version = current.getKey();
            if (currentAPI.hasAutomaticModule() || previousAPI.hasAutomaticModule()) {
                // current, previous or both don't use modules, use collectPackagesForRemoved
                collectPackagesForRemoved(currentAPI, previousAPI, version, pageModel);
            } else {
                // both current and previous use modules, use collectModulesForRemoved
                collectModulesForRemoved(currentAPI, previousAPI, version, pageModel);
            }
            current = previous;
        }

        return pageModel;
    }

    private static void collectPackagesForRemoved(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion version, PageModel pageModel) {
        for (JavaPackage previousPackage : previousAPI.getJavaPackages()) {
            String packageName = previousPackage.getName();
            JavaPackage currentPackage = currentAPI.findJavaPackage(packageName);
            if (currentPackage == null) {
                // the entire package was removed
                if (previousPackage.getJavaModule().isAutomatic() != (currentAPI.findAutomaticJavaModule() == null)) {
                    throw new IllegalStateException("Cannot only allow removed packages if both versions or neither version supports modules");
                }
                JavaModule previousModule = previousPackage.getJavaModule();
                if (previousModule.isAutomatic()) {
                    pageModel.ensurePackageExists(version, packageName);
                } else {
                    pageModel.ensureModuleExists(version, previousModule.getName())
                            .ensurePackageExists(packageName);
                }
            } else {
                // the package was not removed
                Supplier<PagePackage> existingPackage;
                JavaModule currentModule = currentPackage.getJavaModule();
                if (currentModule.isAutomatic()) {
                    existingPackage = () -> pageModel.ensurePackageExists(version, packageName);
                } else {
                    existingPackage = () -> pageModel.ensureModuleExists(version, currentModule.getName())
                            .ensurePackageExists(packageName);
                }
                // check classes
                collectClassesForRemoved(currentPackage, previousPackage, existingPackage);
            }
        }
    }

    private static void collectModulesForRemoved(JavaAPI currentAPI, JavaAPI previousAPI, JavaVersion version, PageModel pageModel) {
        for (JavaModule previousModule : previousAPI.getJavaModules()) {
            String moduleName = previousModule.getName();
            JavaModule currentModule = currentAPI.findJavaModule(moduleName);
            if (currentModule == null) {
                // the entire module was removed
                pageModel.ensureModuleExists(version, moduleName);
            } else {
                // the module was not removed; check packages
                collectPackagesForRemoved(currentModule, previousModule, version, pageModel);
            }
        }
    }

    private static void collectPackagesForRemoved(JavaModule currentModule, JavaModule previousModule, JavaVersion version, PageModel pageModel) {
        for (JavaPackage previousPackage : previousModule.getJavaPackages()) {
            String packageName = previousPackage.getName();
            JavaPackage currentPackage = currentModule.findJavaPackage(packageName);
            if (currentPackage == null) {
                // the entire package was removed, or possibly moved
                JavaPackage possiblyMovedPackage = currentModule.getJavaAPI().findJavaPackage(packageName);
                if (possiblyMovedPackage != null) {
                    LOGGER.warn("Package {} has moved from module {} to {}", packageName, previousModule.getName(), possiblyMovedPackage.getJavaModule().getName());
                }
                pageModel.ensureModuleExists(version, previousModule.getName())
                        .ensurePackageExists(packageName);
            } else {
                // the package was not removed; check classes
                collectClassesForRemoved(currentPackage, previousPackage, () -> pageModel.ensureModuleExists(version, previousModule.getName())
                        .ensurePackageExists(packageName));
            }
        }
    }

    private static void collectClassesForRemoved(JavaPackage currentPackage, JavaPackage previousPackage, Supplier<PagePackage> existingPackage) {

        for (JavaClass previousClass : previousPackage.getJavaClasses()) {
            String className = previousClass.getName();
            JavaClass currentClass = currentPackage.findJavaClass(className);
            if (currentClass == null) {
                // the entire class was removed
                existingPackage.get()
                        .ensureClassExists(className, previousClass.getType(), previousClass.getSuperClass());
            } else {
                // the class was not removed
                // check interfaces
                //collectInterfacesForRemoved(currentClass, previousClass, existingPackage);
                // check members
                collectMembersForRemoved(currentClass, previousClass, existingPackage);
            }
        }
    }

    private static void collectInterfacesForRemoved(JavaClass currentClass, JavaClass previousClass, Supplier<PagePackage> existingPackage) {
        JavaInterfaceList currentInterfaceList = currentClass.getInterfaceList();
        for (String interfaceName : previousClass.getInterfaceList().getInterfaceNames()) {
            if (!currentInterfaceList.hasInterfaceName(interfaceName)) {
                existingPackage.get()
                        .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                        .addInterface(interfaceName);
            }
        }
    }

    private static void collectMembersForRemoved(JavaClass currentClass, JavaClass previousClass, Supplier<PagePackage> existingPackage) {

        for (JavaMember previousMember : previousClass.getJavaMembers()) {
            JavaMember.Type type = previousMember.getType();
            String originalSignature = previousMember.getOriginalSignature();
            JavaMember currentMember = currentClass.findJavaMember(type, originalSignature);
            if (currentMember == null && !isInheritedMethod(currentClass, type, originalSignature)) {
                // the member was removed
                existingPackage.get()
                        .ensureClassExists(currentClass.getName(), currentClass.getType(), currentClass.getSuperClass())
                        .addMember(type, previousMember.getPrettifiedSignature());
            }
            // else the member still exists, either in currentClass itself or as inherited method
        }
    }

    private static boolean isInheritedMethod(JavaClass javaClass, JavaMember javaMember) {
        return isInheritedMethod(javaClass, javaMember.getType(), javaMember.getOriginalSignature());
    }

    private static boolean isInheritedMethod(JavaClass javaClass, JavaMember.Type type, String originalSignature) {
        return type == JavaMember.Type.METHOD && javaClass.isInheritedMethod(originalSignature);
    }

    private static final class SinceHelper {

        private final NavigableMap<JavaVersion, JavaAPI> javaAPIs;
        private final JavaVersion minimalJavaVersion;

        private SinceHelper(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {
            this.javaAPIs = javaAPIs;
            this.minimalJavaVersion = minimalJavaVersion;
        }

        private JavaVersion getSince(JavaModule javaModule) {
            Set<JavaVersion> javaVersions = moduleStream(javaModule)
                    .map(JavaModule::getSince)
                    .filter(Objects::nonNull)
                    .collect(toSet());
            return extractVersion(javaVersions, javaModule);
        }

        private JavaVersion getSince(JavaPackage javaPackage) {
            Set<JavaVersion> javaVersions = packageStream(javaPackage)
                    .map(JavaPackage::getSince)
                    .filter(Objects::nonNull)
                    .collect(toSet());
            return extractVersion(javaVersions, javaPackage);
        }

        private JavaVersion getSince(JavaClass javaClass) {
            Set<JavaVersion> javaVersions = classStream(javaClass)
                    .map(JavaClass::getSince)
                    .filter(Objects::nonNull)
                    .collect(toSet());
            return extractVersion(javaVersions, javaClass);
        }

        private JavaVersion getSince(JavaMember javaMember) {
            Set<JavaVersion> javaVersions = memberStream(javaMember)
                    .map(JavaMember::getSince)
                    .filter(Objects::nonNull)
                    .collect(toSet());
            return extractVersion(javaVersions, javaMember);
        }

        private Stream<JavaModule> moduleStream(JavaModule javaModule) {
            return javaAPIs.values().stream()
                    .map(api -> api.findJavaModule(javaModule.getName()))
                    .filter(Objects::nonNull);
        }

        private Stream<JavaPackage> packageStream(JavaPackage javaPackage) {
            return javaAPIs.values().stream()
                    .map(api -> api.findJavaPackage(javaPackage.getName()))
                    .filter(Objects::nonNull);
        }

        private Stream<JavaClass> classStream(JavaClass javaClass) {
            return packageStream(javaClass.getJavaPackage())
                    .map(p -> p.findJavaClass(javaClass.getName()))
                    .filter(Objects::nonNull);
        }

        private Stream<JavaMember> memberStream(JavaMember javaMember) {
            return classStream(javaMember.getJavaClass())
                    .map(c -> c.findJavaMember(javaMember.getType(), javaMember.getOriginalSignature()))
                    .filter(Objects::nonNull);
        }

        private JavaVersion extractVersion(Set<JavaVersion> javaVersions, Object source) {
            switch (javaVersions.size()) {
            case 0:
                return null;
            case 1:
                return javaVersions.iterator().next();
            default:
                Set<JavaVersion> uniqueVersions = new TreeSet<>(javaVersions);
                if (uniqueVersions.size() == 1) {
                    return javaVersions.iterator().next();
                }
                Set<JavaVersion> tooNewJavaVersions = new TreeSet<>(javaVersions);
                tooNewJavaVersions.removeIf(v -> v.compareTo(minimalJavaVersion) < 0);
                if (tooNewJavaVersions.isEmpty()) {
                    LOGGER.warn("Found multiple versions for {} which are smaller than {}: {}", source, minimalJavaVersion, javaVersions);
                }
                return javaVersions.stream().max(naturalOrder()).get();
            }
        }
    }
}
