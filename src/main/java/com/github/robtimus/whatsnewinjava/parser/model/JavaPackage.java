package com.github.robtimus.whatsnewinjava.parser.model;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Objects.requireNonNull;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.JsonObject;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavaPackage extends VersionableJavaObject {

    private final JavaModule javaModule;

    private final String name;

    private final Map<String, JavaClass> javaClasses;

    JavaPackage(JavaModule javaModule, String name, JavaVersion since, boolean deprecated) {
        super(since, deprecated);

        this.javaModule = requireNonNull(javaModule);

        this.name = requireNonNull(name);

        this.javaClasses = new TreeMap<>();
    }

    public JavaAPI getJavaAPI() {
        return javaModule.getJavaAPI();
    }

    public JavaModule getJavaModule() {
        return javaModule;
    }

    public String getName() {
        return name;
    }

    public Collection<JavaClass> getJavaClasses() {
        return unmodifiableCollection(javaClasses.values());
    }

    public void addJavaClass(String className, JavaClass.Type type,
            String superClass, JavaInterfaceList interfaceList, Collection<String> inheritedMethodSignatures,
            JavaVersion since, boolean deprecated) {

        if (javaClasses.containsKey(className)) {
            throw new IllegalStateException(String.format("Duplicate class: %s.%s", name, className));
        }
        javaClasses.put(className, new JavaClass(this, className, type, superClass, interfaceList, inheritedMethodSignatures, since, deprecated));
    }

    public JavaClass getJavaClass(String className) {
        JavaClass javaClass = findJavaClass(className);
        if (javaClass == null) {
            throw new IllegalStateException(String.format("Could not find class %s.%s", name, className));
        }
        return javaClass;
    }

    public JavaClass findJavaClass(String className) {
        return javaClasses.get(className);
    }

    @Override
    public boolean isDeprecated() {
        return super.isDeprecated() || (javaModule != null && javaModule.isDeprecated());
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    void appendToJSON(JsonObject json) {
        super.appendToJSON(json);

        JsonObject classes = new JsonObject();
        for (JavaClass javaClass : javaClasses.values()) {
            classes.add(javaClass.getName(), javaClass.toJSON());
        }
        json.add("classes", classes);
    }

    static JavaPackage fromJSON(JsonObject json, JavaModule javaModule, String name) {
        JavaVersion since = readSince(json);
        boolean deprecated = readDeprecated(json);

        JavaPackage javaPackage = new JavaPackage(javaModule, name, since, deprecated);

        JsonObject classes = json.get("classes").getAsJsonObject();
        for (String className : classes.keySet()) {
            JsonObject classJSON = classes.get(className).getAsJsonObject();
            JavaClass javaClass = JavaClass.fromJSON(classJSON, javaPackage, className);
            javaPackage.javaClasses.put(javaClass.getName(), javaClass);
        }

        return javaPackage;
    }
}
