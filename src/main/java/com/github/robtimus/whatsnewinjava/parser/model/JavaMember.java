package com.github.robtimus.whatsnewinjava.parser.model;

import static java.util.Objects.requireNonNull;
import com.google.gson.JsonObject;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavaMember extends VersionableJavaObject {

    private final JavaClass javaClass;

    private final Type type;
    private final String originalSignature;
    private final String prettifiedSignature;

    JavaMember(JavaClass javaClass, Type type, String signature, JavaVersion since, boolean deprecated) {
        super(since, deprecated);

        this.javaClass = requireNonNull(javaClass);

        this.type = requireNonNull(type);
        this.originalSignature = requireNonNull(signature);
        this.prettifiedSignature = prettifySignature(signature).replace("<init>", javaClass.getName());
    }

    public JavaAPI getJavaAPI() {
        return javaClass.getJavaAPI();
    }

    public JavaClass getJavaClass() {
        return javaClass;
    }

    public Type getType() {
        return type;
    }

    public String getOriginalSignature() {
        return originalSignature;
    }

    public String getPrettifiedSignature() {
        return prettifiedSignature;
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

    @Override
    public String toString() {
        return javaClass + "." + getPrettifiedSignature();
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
