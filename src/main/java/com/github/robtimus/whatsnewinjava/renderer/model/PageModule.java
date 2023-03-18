/*
 * PageModule.java
 * Copyright 2019 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.whatsnewinjava.renderer.model;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Objects.requireNonNull;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("javadoc")
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

    public boolean hasContent() {
        return !packages.isEmpty();
    }

    @Override
    public String toString() {
        return name;
    }

    PagePackage ensurePackageExists(String packageName) {
        return packages.computeIfAbsent(packageName, k -> new PagePackage(this, packageName));
    }

    PagePackage findPackage(String packageName) {
        return packages.get(packageName);
    }
}
