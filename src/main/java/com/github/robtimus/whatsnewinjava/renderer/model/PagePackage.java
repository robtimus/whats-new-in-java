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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;

@SuppressWarnings({ "nls", "javadoc" })
public final class PagePackage {

    private static final Logger LOGGER = LoggerFactory.getLogger(PagePackage.class);

    private final PageModule pageModule;

    private final String name;

    private final Map<String, PageClass> classes;

    private String newModuleName;

    PagePackage(PageModule pageModule, String name) {
        this.pageModule = pageModule;

        this.name = requireNonNull(name);

        this.classes = new TreeMap<>();
    }

    public PageModule pageModule() {
        return pageModule;
    }

    public String name() {
        return name;
    }

    public Collection<PageClass> classes() {
        return unmodifiableCollection(classes.values());
    }

    public String newModuleName() {
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
            throw new IllegalStateException("Cannot add classes to package %s which is marked as moved to new module %s".formatted(name, newModuleName));
        }
        PageClass result = classes.computeIfAbsent(className, k -> new PageClass(this, className, type, superClass));
        if (type != result.type()) {
            LOGGER.warn("Type has changed for class {}.{} from {} to {}", name, className, result.type(), type);
        }
        return result;
    }

    void movedToNewModule(String newModuleName) {
        if (!classes.isEmpty()) {
            throw new IllegalStateException("Cannot mark package %s as moved to new module if it has nested classes".formatted(name));
        }
        this.newModuleName = newModuleName;
    }
}
