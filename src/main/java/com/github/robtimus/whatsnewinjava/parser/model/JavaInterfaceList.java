package com.github.robtimus.whatsnewinjava.parser.model;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableCollection;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("javadoc")
public final class JavaInterfaceList {

    public static final JavaInterfaceList EMPTY = new JavaInterfaceList(emptySet());

    private final Map<String, String> interfaceNames;

    public JavaInterfaceList(Collection<String> interfaceNames) {
        this.interfaceNames = new TreeMap<>();
        for (String interfaceName : interfaceNames) {
            this.interfaceNames.put(getRawType(interfaceName), interfaceName);
        }
    }

    public Collection<String> getInterfaceNames() {
        return unmodifiableCollection(interfaceNames.values());
    }

    public boolean hasInterfaceName(String interfaceName) {
        return interfaceNames.containsKey(getRawType(interfaceName));
    }

    public String getGenericInterfaceName(String interfaceName) {
        return interfaceNames.get(getRawType(interfaceName));
    }

    private String getRawType(String interfaceName) {
        int index = interfaceName.indexOf('<');
        return index == -1 ? interfaceName : interfaceName.substring(0, index);
    }

    @Override
    public String toString() {
        return interfaceNames.values().toString();
    }
}
