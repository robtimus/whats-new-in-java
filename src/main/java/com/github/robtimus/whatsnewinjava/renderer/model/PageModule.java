package com.github.robtimus.whatsnewinjava.renderer.model;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Objects.requireNonNull;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public final class PageModule {

    private final String name;

    private final Map<String, PagePackage> packages;

    PageModule(String name) {
        this.name = requireNonNull(name);

        this.packages = new TreeMap<>();
    }

    public String getName() {
        return name;
    }

    public Collection<PagePackage> getPackages() {
        return unmodifiableCollection(packages.values());
    }

    @Override
    public String toString() {
        return name;
    }

    PagePackage ensurePackageExists(String packageName) {
        return packages.computeIfAbsent(packageName, k -> new PagePackage(this, packageName));
    }
}
