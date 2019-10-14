package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class JavaInterfaceList {

    public static final JavaInterfaceList EMPTY = new JavaInterfaceList(Collections.emptySet());

    private final Set<String> interfaceNames;
    private final Set<String> previous;
    private final ChangeType changeType;

    public JavaInterfaceList(Set<String> interfaceNames) {
        this(new TreeSet<>(interfaceNames), null, null);
    }

    private JavaInterfaceList(Set<String> interfaceNames, Set<String> previous, ChangeType changeType) {
        this.interfaceNames = interfaceNames;
        this.previous = previous;
        this.changeType = changeType;
    }

    public Set<String> getInterfaceNames() {
        return Collections.unmodifiableSet(interfaceNames);
    }

    public boolean hasDifferences() {
        return changeType != null && changeType.hasDifferences(this);
    }

    public Set<String> getDifferences() {
        return changeType == null ? Collections.emptySet() : changeType.getDifferences(this);
    }

    JavaInterfaceList fromPrevious(JavaInterfaceList previousInterfaceList, ChangeType changeType) {
        if (previous != null) {
            throw new IllegalStateException("previous should not be set yet");
        }
        return previousInterfaceList == null || interfaceNames.equals(previousInterfaceList.interfaceNames)
                ? this
                : new JavaInterfaceList(interfaceNames, previousInterfaceList.interfaceNames, changeType);
    }

    @Override
    public String toString() {
        return previous == null ? interfaceNames.toString() : interfaceNames + " (was " + previous + ")";
    }

    public enum ChangeType {
        ADDED {
            @Override
            boolean hasDifferences(JavaInterfaceList javaInterfaceList) {
                return !javaInterfaceList.previous.containsAll(javaInterfaceList.interfaceNames);
            }

            @Override
            Set<String> getDifferences(JavaInterfaceList javaInterfaceList) {
                Set<String> interfaceNames = new TreeSet<>(javaInterfaceList.interfaceNames);
                interfaceNames.removeAll(javaInterfaceList.previous);
                return interfaceNames;
            }
        },
        REMOVED {
            @Override
            boolean hasDifferences(JavaInterfaceList javaInterfaceList) {
                return !javaInterfaceList.interfaceNames.containsAll(javaInterfaceList.previous);
            }

            @Override
            Set<String> getDifferences(JavaInterfaceList javaInterfaceList) {
                Set<String> interfaceNames = new TreeSet<>(javaInterfaceList.previous);
                interfaceNames.removeAll(javaInterfaceList.interfaceNames);
                return interfaceNames;
            }
        },
        ;

        abstract boolean hasDifferences(JavaInterfaceList javaInterfaceList);

        abstract Set<String> getDifferences(JavaInterfaceList javaInterfaceList);
    }
}
