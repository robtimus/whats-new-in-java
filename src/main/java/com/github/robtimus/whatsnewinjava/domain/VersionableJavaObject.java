package com.github.robtimus.whatsnewinjava.domain;

public abstract class VersionableJavaObject {

    private final JavaVersion since;

    VersionableJavaObject(JavaVersion since) {
        this.since = since;
    }

    public JavaVersion getSince() {
        return since;
    }
}
