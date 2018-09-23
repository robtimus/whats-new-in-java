package com.github.robtimus.whatsnewinjava.domain;

import java.util.Objects;
import java.util.stream.Stream;
import com.google.gson.JsonObject;

public final class JavaMember extends VersionableJavaObject {

    private final JavaClass javaClass;
    private final String signature;
    private final Type type;

    private final String javadocBaseURL;

    JavaMember(JavaClass javaClass, Type type, String signature, JavaVersion since, boolean deprecated, String javadocBaseURL) {
        super(since, deprecated);
        this.javaClass = javaClass;
        this.type = type;
        this.signature = signature;
        this.javadocBaseURL = javadocBaseURL;
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

    public String getJavadocBaseURL() {
        return javadocBaseURL;
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

    static JavaMember fromJSON(JsonObject json, JavaClass javaClass, Type type, String signature) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        return new JavaMember(javaClass, type, signature, since, deprecated, javaClass.getJavadocBaseURL());
    }

    public enum Type {
        CONSTRUCTOR,
        METHOD,
        FIELD,
    }
}
