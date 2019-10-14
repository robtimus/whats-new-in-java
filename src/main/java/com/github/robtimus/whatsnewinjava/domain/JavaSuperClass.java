package com.github.robtimus.whatsnewinjava.domain;

import java.util.Objects;

public final class JavaSuperClass {

    private final String fullName;
    private final String previous;

    public JavaSuperClass(String fullName) {
        this(fullName, null);
    }

    private JavaSuperClass(String fullName, String previous) {
        this.fullName = Objects.requireNonNull(fullName);
        this.previous = previous;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPrevious() {
        return previous;
    }

    public boolean hasPrevious() {
        return previous != null;
    }

    JavaSuperClass fromPrevious(JavaSuperClass previousSuperClass) {
        if (previous != null) {
            throw new IllegalStateException("previous should not be set yet");
        }
        return previousSuperClass == null || fullName.equals(previousSuperClass.fullName)
                ? this
                : new JavaSuperClass(fullName, previousSuperClass.fullName);
    }

    @Override
    public String toString() {
        return previous == null ? fullName : fullName + " (was " + previous + ")";
    }
}
