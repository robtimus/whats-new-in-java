package com.github.robtimus.whatsnewinjava.domain;

public final class JavaMember extends VersionableJavaObject {

    private final JavaClass javaClass;
    private final String signature;

    JavaMember(JavaClass javaClass, String signature, JavaVersion since) {
        super(since);
        this.javaClass = javaClass;
        this.signature = signature;
    }

    public JavaClass getJavaClass() {
        return javaClass;
    }

    public String getSignature() {
        return signature;
    }

    public String getConstructorSafeSignature() {
        return signature.replace("<init>", javaClass.getName());
    }
}
