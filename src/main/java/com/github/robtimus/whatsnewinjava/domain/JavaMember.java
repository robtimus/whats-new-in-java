package com.github.robtimus.whatsnewinjava.domain;

import java.util.Objects;
import java.util.stream.Stream;
import com.google.gson.JsonObject;

public final class JavaMember extends VersionableJavaObject {

    private final JavaClass javaClass;
    private final String signature;
    private final Type type;

    private final Javadoc javadoc;

    JavaMember(JavaClass javaClass, Type type, String signature, JavaVersion since, boolean deprecated) {
        super(since, deprecated);
        this.javaClass = javaClass;
        this.type = type;
        this.signature = signature;
        this.javadoc = javaClass.getJavadoc();
    }

    public JavaClass getJavaClass() {
        return javaClass;
    }

    public Type getType() {
        return type;
    }

    public String getSignature() {
        return signature;
    }

    public String getPrettySignature() {
        return prettifySignature(signature).replace("<init>", javaClass.getName());
    }

    public Javadoc getJavadoc() {
        return javadoc;
    }

    static String prettifySignature(String signature) {
        return signature
                .replaceFirst("-", "\\(")
                .replaceFirst("-$", "\\)")
                .replace("-", ",")
                .replace(":A", "[]")
                .replaceFirst("^Z:Z_", "_")
                .replace(" ", "")
                ;
    }

    Stream<JavaVersion> allSinceValues() {
        return Stream.of(getSince())
                .filter(Objects::nonNull);
    }

    @Override
    public String toString() {
        return javaClass + "." + getPrettySignature();
    }

    JavaMember copy(JavaClass copyClass) {
        return new JavaMember(copyClass, type, signature, getSince(), isDeprecated());
    }

    static JavaMember fromJSON(JsonObject json, JavaClass javaClass, Type type, String signature) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        return new JavaMember(javaClass, type, signature, since, deprecated);
    }

    public enum Type {
        CONSTRUCTOR,
        METHOD,
        FIELD,
    }
}
