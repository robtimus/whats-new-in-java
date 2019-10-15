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
        this.fullName = requireNonNull(pagePackage.getName()) + "." + name;
        this.type = requireNonNull(type);

        this.superClass = superClass;
        this.interfaceList = new LinkedHashSet<>();
        this.alteredInterfaces = new LinkedHashMap<>();

        this.members = new TreeSet<>(comparing(PageMember::getSignatureForCompare).thenComparing(PageMember::getType));
    }

    public PagePackage getPagePackage() {
        return pagePackage;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public JavaClass.Type getType() {
        return type;
    }

    public String getSuperClass() {
        return superClass;
    }

    public String getPreviousClass() {
        return previousSuperClass;
    }

    public Set<String> getInterfaceList() {
        return unmodifiableSet(interfaceList);
    }

    public Map<String, String> getAlteredInterfaces() {
        return unmodifiableMap(alteredInterfaces);
    }

    public Collection<PageMember> getMembers() {
        return unmodifiableCollection(members);
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
