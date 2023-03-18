/*
 * PagePackage.java
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
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;

@SuppressWarnings({ "nls", "javadoc" })
public final class PagePackage {

    private final PageModule pageModule;

    private final String name;

    private final Map<String, PageClass> classes;

    private String newModuleName;

    PagePackage(PageModule pageModule, String name) {
        this.pageModule = pageModule;

        this.name = requireNonNull(name);

        this.classes = new TreeMap<>();
    }

    public PageModule getPageModule() {
        return pageModule;
    }

    public String getName() {
        return name;
    }

    public Collection<PageClass> getClasses() {
        return unmodifiableCollection(classes.values());
    }

    public String getNewModuleName() {
        return newModuleName;
    }

    public boolean hasContent() {
        return !classes.isEmpty();
    }

    public boolean isMovedToNewModule() {
        return newModuleName != null;
    }

    @Override
    public String toString() {
        return name;
    }

    PageClass ensureClassExists(String className, JavaClass.Type type, String superClass) {
        if (newModuleName != null) {
            throw new IllegalStateException(String.format("Cannot add classes to package %s which is marked as moved to new module %s", name, newModuleName));
        }
        PageClass result = classes.computeIfAbsent(className, k -> new PageClass(this, className, type, superClass));
        if (type != result.getType()) {
            throw new IllegalStateException(String.format("Non-matching Java class encountered for class %s.%s; expected type %s, was %s",
                    name, className, type, result.getType()));
        }
        return result;
    }

    void movedToNewModule(String newModuleName) {
        if (!classes.isEmpty()) {
            throw new IllegalStateException(String.format("Cannot mark package %s as moved to new module if it has nested classes", name));
        }
        this.newModuleName = newModuleName;
    }
}
