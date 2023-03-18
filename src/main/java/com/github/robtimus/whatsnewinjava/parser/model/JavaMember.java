/*
 * JavaMember.java
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
