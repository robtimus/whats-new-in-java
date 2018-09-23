package com.github.robtimus.whatsnewinjava.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.github.robtimus.whatsnewinjava.domain.JavaMember.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class JavaClass extends VersionableJavaObject {

    private final JavaPackage javaPackage;
    private final String name;
    private final Map<MemberMapKey, JavaMember> javaMembers;
    private final Map<String, Signature> inheritedMethodSignatures;

    private final String javadocBaseURL;

    JavaClass(JavaPackage javaPackage, String name, JavaVersion since, boolean deprecated, Collection<String> inheritedMethodSignatures, String javadocBaseURL) {
        super(since, deprecated);
        this.javaPackage = Objects.requireNonNull(javaPackage);
        this.name = Objects.requireNonNull(name);
        this.javaMembers = new TreeMap<>();
        this.inheritedMethodSignatures = inheritedMethodSignatures.stream()
                .collect(Collectors.toMap(Function.identity(), Signature::new, (x, y) -> { throw new IllegalStateException(); }, TreeMap::new));
        this.javadocBaseURL = javadocBaseURL;
    }

    public JavaPackage getJavaPackage() {
        return javaPackage;
    }

    public String getName() {
        return name;
    }

    public String getJavadocBaseURL() {
        return javadocBaseURL;
    }

    public Collection<JavaMember> getJavaMembers() {
        return Collections.unmodifiableCollection(javaMembers.values());
    }

    public Collection<String> getInheritedMethodSignatures() {
        return Collections.unmodifiableCollection(inheritedMethodSignatures.keySet());
    }

    boolean hasJavaMembers() {
        return !javaMembers.isEmpty();
    }

    boolean hasJavaMembers(JavaVersion since) {
        return javaMembers.values().stream()
                .anyMatch(matchesSince(since));
    }

    public Collection<JavaMember> getJavaMembers(JavaVersion since) {
        return javaMembers.values().stream()
                .filter(matchesSince(since))
                .collect(Collectors.toList());
    }

    private Predicate<JavaMember> matchesSince(JavaVersion since) {
        return m -> m.getSince() == since && !since.equals(this.getSince());
    }

    void addJavaMember(JavaMember.Type type, String signature, JavaVersion since, boolean deprecated) {
        final MemberMapKey key = new MemberMapKey(type, signature);
        if (javaMembers.containsKey(key)) {
            throw new IllegalStateException(String.format("Duplicate signature for class %s.%s: %s %s", javaPackage.getName(), name, type, signature));
        }
        javaMembers.put(key, new JavaMember(this, type, signature, since, deprecated, javadocBaseURL));
    }

    JavaMember findJavaMember(JavaMember.Type type, String signature) {
        final MemberMapKey key = new MemberMapKey(type, signature);
        return javaMembers.get(key);
    }

    boolean isInheritedMethod(String signature) {
        Signature sig = new Signature(signature);
        return inheritedMethodSignatures.values().stream()
                .anyMatch(s -> s.matches(sig));
    }

    Stream<JavaVersion> allSinceValues() {
        Stream<JavaVersion> ownSince = Stream.of(getSince());
        Stream<JavaVersion> memberSinceValues = javaMembers.values().stream()
                .flatMap(JavaMember::allSinceValues);
        return Stream.concat(ownSince, memberSinceValues)
                .filter(Objects::nonNull);
    }

    void retainSince(JavaVersion minimalJavaVersion) {
        javaMembers.values().removeIf(m -> !m.hasMinimalSince(minimalJavaVersion));
    }

    @Override
    public String toString() {
        return javaPackage + "." + name;
    }

    @Override
    void appendToJSON(JsonObject json) {
        super.appendToJSON(json);

        addMembers("constructors", JavaMember.Type.CONSTRUCTOR, json);
        addMembers("fields", JavaMember.Type.FIELD, json);
        addMembers("methods", JavaMember.Type.METHOD, json);

        JsonArray inheritedMethods = inheritedMethodSignatures.keySet().stream()
                .collect(Collector.of(JsonArray::new, JsonArray::add, (x, y) -> { throw new IllegalStateException(); }));
        json.add("inheritedMethods", inheritedMethods);
    }

    private void addMembers(String propertyName, JavaMember.Type type, JsonObject json) {
        JsonObject members = javaMembers.values().stream()
                .filter(m -> m.getType() == type)
                .collect(Collector.of(JsonObject::new, (o, m) -> o.add(m.getSignature(), m.toJSON()), (x, y) -> { throw new IllegalStateException(); }));

        json.add(propertyName, members);
    }

    static JavaClass fromJSON(JsonObject json, JavaPackage javaPackage, String name) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        JsonArray inheritedMethods = json.get("inheritedMethods").getAsJsonArray();
        Set<String> inheritedMethodSignatures = new TreeSet<>();
        for (int i = 0, size = inheritedMethods.size(); i < size; i++) {
            inheritedMethodSignatures.add(inheritedMethods.get(i).getAsString());
        }

        JavaClass javaClass = new JavaClass(javaPackage, name, since, deprecated, inheritedMethodSignatures, javaPackage.getJavadocBaseURL());

        addMembers(json, "constructors", JavaMember.Type.CONSTRUCTOR, javaClass);
        addMembers(json, "fields", JavaMember.Type.FIELD, javaClass);
        addMembers(json, "methods", JavaMember.Type.METHOD, javaClass);

        return javaClass;
    }

    private static void addMembers(JsonObject json, String propertyName, JavaMember.Type type, JavaClass javaClass) {
        JsonObject members = json.get(propertyName).getAsJsonObject();
        for (String signature : members.keySet()) {
            JsonObject memberJSON = members.get(signature).getAsJsonObject();
            JavaMember javaMember = JavaMember.fromJSON(memberJSON, javaClass, type, signature);
            final MemberMapKey key = new MemberMapKey(type, javaMember.getSignature());
            javaClass.javaMembers.put(key, javaMember);
        }
    }

    private static final class MemberMapKey implements Comparable<MemberMapKey> {

        private static final Comparator<MemberMapKey> COMPARATOR = Comparator.comparing(MemberMapKey::getSignature)
                .thenComparing(MemberMapKey::getType);

        private final JavaMember.Type type;
        private final String signature;

        private MemberMapKey(Type type, String signature) {
            this.type = type;
            this.signature = normalizeSignature(signature, type);
        }

        private String normalizeSignature(String signature, Type type) {
            String prettified = JavaMember.prettifySignature(signature);
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
    }

    private static final class Signature {

        private static final Pattern ARGUMENT_LIST_SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");
        private static final Pattern GENERIC_TYPE_PATTERN = Pattern.compile("[A-Z][A-Za-z_]*");

        private final String prettifiedSignature;
        private final String methodName;
        private final List<String> argumentTypes;

        private Signature(String signature) {
            prettifiedSignature = JavaMember.prettifySignature(signature);

            int index = prettifiedSignature.indexOf('(');
            methodName = prettifiedSignature.substring(0, index);

            if (prettifiedSignature.endsWith("()")) {
                argumentTypes = Collections.emptyList();
            } else {
                String argumentList = prettifiedSignature.substring(index + 1, prettifiedSignature.indexOf(')'));
                argumentTypes = ARGUMENT_LIST_SPLIT_PATTERN.splitAsStream(argumentList)
                        .collect(Collectors.toList());
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
