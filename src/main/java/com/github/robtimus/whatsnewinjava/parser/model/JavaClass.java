/*
 * JavaClass.java
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

import static com.github.robtimus.whatsnewinjava.parser.model.JavaMember.prettifySignature;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavaClass extends VersionableJavaObject {

    private final JavaPackage javaPackage;

    private final String name;
    private final Type type;

    private final String superClass;
    private final JavaInterfaceList interfaceList;

    private final Map<MemberMapKey, JavaMember> javaMembers;
    private final Map<String, Signature> inheritedMethodSignatures;

    JavaClass(JavaPackage javaPackage, String name, Type type,
            String superClass, JavaInterfaceList interfaceList, Collection<String> inheritedMethodSignatures,
            JavaVersion since, boolean deprecated) {

        super(since, deprecated);

        this.javaPackage = requireNonNull(javaPackage);

        this.name = requireNonNull(name);
        this.type = requireNonNull(type);

        if (!type.isInterface() && !"java.lang".equals(javaPackage.name()) && !"Object".equals(name)) {
            requireNonNull(superClass);
        }
        this.superClass = superClass;
        this.interfaceList = requireNonNull(interfaceList);

        this.javaMembers = new TreeMap<>();
        this.inheritedMethodSignatures = inheritedMethodSignatures.stream()
                .collect(toMap(identity(), Signature::new, throwingMerger(), TreeMap::new));
    }

    public JavaAPI javaAPI() {
        return javaPackage.javaAPI();
    }

    public JavaPackage javaPackage() {
        return javaPackage;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public String superClass() {
        return superClass;
    }

    public JavaInterfaceList interfaceList() {
        return interfaceList;
    }

    public Collection<JavaMember> javaMembers() {
        return unmodifiableCollection(javaMembers.values());
    }

    public void addJavaMember(JavaMember.Type type, String signature, JavaVersion since, boolean deprecated) {
        final MemberMapKey key = new MemberMapKey(type, signature);
        if (javaMembers.containsKey(key)) {
            throw new IllegalStateException("Duplicate signature for class %s.%s: %s %s".formatted(javaPackage.name(), name, type, signature));
        }
        javaMembers.put(key, new JavaMember(this, type, signature, since, deprecated));
    }

    public JavaMember getJavaMember(JavaMember.Type type, String signature) {
        JavaMember javaMember = findJavaMember(type, signature);
        if (javaMember == null) {
            throw new IllegalStateException("Could not find member in class %s.%s: %s %s".formatted(javaPackage.name(), name, type, signature));
        }
        return javaMember;
    }

    public JavaMember findJavaMember(JavaMember.Type type, String signature) {
        final MemberMapKey key = new MemberMapKey(type, signature);
        return javaMembers.get(key);
    }

    public boolean isInheritedMethod(String signature) {
        Signature sig = new Signature(signature);
        return inheritedMethodSignatures.values().stream()
                .anyMatch(s -> s.matches(sig));
    }

    @Override
    public String toString() {
        return javaPackage + "." + name;
    }

    @Override
    void appendToJSON(JsonObject json) {
        json.addProperty("type", type.name().toLowerCase());

        super.appendToJSON(json);

        if (superClass != null) {
            json.addProperty("superClass", superClass);
        }
        JsonArray interfaces = interfaceList.interfaceNames().stream()
                .collect(Collector.of(JsonArray::new, JsonArray::add, throwingMerger()));
        json.add("interfaces", interfaces);

        addMembers("constructors", JavaMember.Type.CONSTRUCTOR, json);
        addMembers("fields", JavaMember.Type.FIELD, json);
        addMembers("methods", JavaMember.Type.METHOD, json);

        JsonArray inheritedMethods = inheritedMethodSignatures.keySet().stream()
                .collect(Collector.of(JsonArray::new, JsonArray::add, throwingMerger()));
        json.add("inheritedMethods", inheritedMethods);
    }

    private void addMembers(String propertyName, JavaMember.Type type, JsonObject json) {
        JsonObject members = javaMembers.values().stream()
                .filter(m -> m.type() == type)
                .collect(Collector.of(JsonObject::new, (o, m) -> o.add(m.originalSignature(), m.toJSON()), throwingMerger()));

        json.add(propertyName, members);
    }

    static JavaClass fromJSON(JsonObject json, JavaPackage javaPackage, String name) {
        Type type = Type.valueOf(json.get("type").getAsString().toUpperCase());

        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        String superClass = readSuperClass(json);

        JsonArray interfaces = json.get("interfaces").getAsJsonArray();
        Set<String> interfaceNames = new TreeSet<>();
        for (int i = 0, size = interfaces.size(); i < size; i++) {
            interfaceNames.add(interfaces.get(i).getAsString());
        }
        JavaInterfaceList interfaceList = new JavaInterfaceList(interfaceNames);

        JsonArray inheritedMethods = json.get("inheritedMethods").getAsJsonArray();
        Set<String> inheritedMethodSignatures = new TreeSet<>();
        for (int i = 0, size = inheritedMethods.size(); i < size; i++) {
            inheritedMethodSignatures.add(inheritedMethods.get(i).getAsString());
        }

        JavaClass javaClass = new JavaClass(javaPackage, name, type, superClass, interfaceList, inheritedMethodSignatures, since, deprecated);

        addMembers(json, "constructors", JavaMember.Type.CONSTRUCTOR, javaClass);
        addMembers(json, "fields", JavaMember.Type.FIELD, javaClass);
        addMembers(json, "methods", JavaMember.Type.METHOD, javaClass);

        return javaClass;
    }

    private static String readSuperClass(JsonObject json) {
        JsonElement superClass = json.get("superClass");
        return superClass == null ? null : superClass.getAsString();
    }

    private static void addMembers(JsonObject json, String propertyName, JavaMember.Type type, JavaClass javaClass) {
        JsonObject members = json.get(propertyName).getAsJsonObject();
        for (String signature : members.keySet()) {
            JsonObject memberJSON = members.get(signature).getAsJsonObject();
            JavaMember javaMember = JavaMember.fromJSON(memberJSON, javaClass, type, signature);
            final MemberMapKey key = new MemberMapKey(type, javaMember.originalSignature());
            javaClass.javaMembers.put(key, javaMember);
        }
    }

    private static <T> BinaryOperator<T> throwingMerger() {
        return (t, u) -> {
            throw new IllegalStateException();
        };
    }

    public enum Type {
        CLASS(false),
        ENUM(false),
        INTERFACE(true),
        ANNOTATION(true),
        RECORD(false),
        ;

        private final boolean isInterface;

        Type(boolean isInterface) {
            this.isInterface = isInterface;
        }

        public boolean isInterface() {
            return isInterface;
        }
    }

    private record MemberMapKey(JavaMember.Type type, String signature) implements Comparable<MemberMapKey> {

        private static final Comparator<MemberMapKey> COMPARATOR = comparing(MemberMapKey::getSignature)
                .thenComparing(MemberMapKey::getType);

        private MemberMapKey(JavaMember.Type type, String signature) {
            this.type = type;
            this.signature = normalizeSignature(signature, type);
        }

        private String normalizeSignature(String signature, JavaMember.Type type) {
            String prettified = prettifySignature(signature);
            if (type != JavaMember.Type.CONSTRUCTOR) {
                return prettified;
            }
            return prettified.replaceFirst("^.*\\(", "<init>\\(");
        }

        private JavaMember.Type getType() {
            return type;
        }

        private String getSignature() {
            return signature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            JavaClass.MemberMapKey other = (JavaClass.MemberMapKey) o;
            return type == other.type && signature.equals(other.signature);
        }

        @Override
        public int hashCode() {
            return type.hashCode() * 31 + signature.hashCode();
        }

        @Override
        public int compareTo(MemberMapKey o) {
            return COMPARATOR.compare(this, o);
        }

        @Override
        public String toString() {
            return signature;
        }
    }

    private static final class Signature {

        private static final Pattern ARGUMENT_LIST_SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");
        private static final Pattern GENERIC_TYPE_PATTERN = Pattern.compile("[A-Z][A-Za-z_]*");

        private final String prettifiedSignature;
        private final String methodName;
        private final List<String> argumentTypes;

        private Signature(String signature) {
            prettifiedSignature = prettifySignature(signature);

            int index = prettifiedSignature.indexOf('(');
            methodName = prettifiedSignature.substring(0, index);

            if (prettifiedSignature.endsWith("()")) {
                argumentTypes = emptyList();
            } else {
                String argumentList = prettifiedSignature.substring(index + 1, prettifiedSignature.indexOf(')'));
                argumentTypes = ARGUMENT_LIST_SPLIT_PATTERN.splitAsStream(argumentList)
                        .toList();
            }
        }

        private boolean matches(Signature other) {
            if (prettifiedSignature.equals(other.prettifiedSignature)) {
                return true;
            }
            if (!methodName.equals(other.methodName)) {
                return false;
            }
            if (argumentTypes.size() != other.argumentTypes.size()) {
                return false;
            }
            if (argumentTypes.equals(other.argumentTypes)) {
                return true;
            }
            for (Iterator<String> i1 = argumentTypes.iterator(), i2 = other.argumentTypes.iterator(); i1.hasNext() && i2.hasNext(); ) {
                String argumentType1 = i1.next();
                String argumentType2 = i2.next();
                if (!matches(argumentType1, argumentType2)) {
                    return false;
                }
            }
            return true;
        }

        private boolean matches(String argumentType1, String argumentType2) {
            return argumentType1.equals(argumentType2)
                    || isGenericType(argumentType1)
                    || isGenericType(argumentType2);
        }

        private boolean isGenericType(String argumentType) {
            return GENERIC_TYPE_PATTERN.matcher(argumentType).matches();
        }
    }
}
