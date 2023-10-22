/*
 * PageClass.java
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
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass.Type;
import com.github.robtimus.whatsnewinjava.parser.model.JavaMember;

@SuppressWarnings({ "nls", "javadoc" })
public final class PageClass {

    private final PagePackage pagePackage;

    private final String name;
    private final String fullName;
    private final JavaClass.Type type;

    private final String superClass;
    private String previousSuperClass;
    private final Set<String> interfaceList;
    private final Map<String, String> alteredInterfaces;

    private final Set<PageMember> members;

    PageClass(PagePackage pagePackage, String name, Type type, String superClass) {
        this.pagePackage = requireNonNull(pagePackage);

        this.name = requireNonNull(name);
        this.fullName = requireNonNull(pagePackage.name()) + "." + name;
        this.type = requireNonNull(type);

        this.superClass = superClass;
        this.interfaceList = new LinkedHashSet<>();
        this.alteredInterfaces = new LinkedHashMap<>();

        this.members = new TreeSet<>(comparing(PageMember::signatureForCompare).thenComparing(PageMember::type));
    }

    public PagePackage pagePackage() {
        return pagePackage;
    }

    public String name() {
        return name;
    }

    public String fullName() {
        return fullName;
    }

    public JavaClass.Type type() {
        return type;
    }

    public String superClass() {
        return superClass;
    }

    public String previousClass() {
        return previousSuperClass;
    }

    public Set<String> interfaceList() {
        return unmodifiableSet(interfaceList);
    }

    public Map<String, String> alteredInterfaces() {
        return unmodifiableMap(alteredInterfaces);
    }

    public Collection<PageMember> members() {
        return unmodifiableCollection(members);
    }

    public boolean hasContent() {
        return !members.isEmpty();
    }

    @Override
    public String toString() {
        return fullName;
    }

    void setPreviousSuperClass(String previousSuperClass) {
        requireNonNull(previousSuperClass);
        if (this.previousSuperClass != null) {
            throw new IllegalStateException("Previous super class is already set");
        }
        this.previousSuperClass = previousSuperClass;
    }

    void addInterface(String interfaceName) {
        interfaceList.add(interfaceName);
    }

    void addAlteredInterface(String currentInterfaceName, String previousInterfaceName) {
        alteredInterfaces.put(currentInterfaceName, previousInterfaceName);
    }

    void addMember(JavaMember.Type type, String signature) {
        members.add(new PageMember(this, type, signature));
    }
}
