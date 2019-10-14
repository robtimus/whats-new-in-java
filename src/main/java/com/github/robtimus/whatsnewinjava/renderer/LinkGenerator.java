package com.github.robtimus.whatsnewinjava.renderer;

import java.util.NavigableMap;
import java.util.Objects;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.domain.JavaClass;
import com.github.robtimus.whatsnewinjava.domain.JavaMember;
import com.github.robtimus.whatsnewinjava.domain.JavaModule;
import com.github.robtimus.whatsnewinjava.domain.JavaPackage;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;

final class LinkGenerator {

    private final NavigableMap<JavaVersion, JavaAPI> javaAPIs;
    private final String latestBaseURL;

    LinkGenerator(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        this.javaAPIs = javaAPIs;
        latestBaseURL = javaAPIs.isEmpty() ? "" : javaAPIs.lastEntry().getValue().getJavadoc().getBaseURL();
    }

    public String getLatestBaseURL() {
        return latestBaseURL;
    }

    public String generateLink(JavaModule javaModule) {
        JavaModule latestModule = findLatestModule(javaModule);
        String format = latestModule.getJavadoc().useModules() ? "%s%s/module-summary.html" : "%s%s-summary.html";
        return String.format(format, latestModule.getJavadoc().getBaseURL(), javaModule.getName())
                .replace(latestBaseURL, "");
    }

    public String generateLink(JavaPackage javaPackage) {
        JavaPackage latestPackage = findLatestPackage(javaPackage);
        return String.format("%s%s/package-summary.html", latestPackage.getJavadoc().getBaseURL(), getRelativeBaseURL(latestPackage))
                .replace(latestBaseURL, "");
    }

    public String generateLink(JavaClass javaClass) {
        JavaClass latestClass = findLatestClass(javaClass);
        return String.format("%s%s/%s.html", latestClass.getJavadoc().getBaseURL(), getRelativeBaseURL(latestClass.getJavaPackage()), latestClass.getName())
                .replace(latestBaseURL, "");
    }

    public String generateLink(JavaMember javaMember) {
        JavaMember latestMember = findLatestMember(javaMember);
        JavaClass latestClass = latestMember.getJavaClass();
        return String.format("%s%s/%s.html#%s", latestClass.getJavadoc().getBaseURL(), getRelativeBaseURL(latestClass.getJavaPackage()), latestClass.getName(), latestMember.getOriginalSignature())
                .replace(latestBaseURL, "");
    }

    private String getRelativeBaseURL(JavaPackage javaPackage) {
        JavaModule javaModule = javaPackage.getJavaModule();
        String packagePath = javaPackage.getName().replace('.', '/');

        return javaPackage.getJavadoc().useModules() ? javaModule.getName() + "/" + packagePath : packagePath;
    }

    private JavaModule findLatestModule(JavaModule javaModule) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaModule(javaModule.getName()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(javaModule);
    }

    private JavaPackage findLatestPackage(JavaPackage javaPackage) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(javaPackage.getName()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(javaPackage);
    }

    private JavaClass findLatestClass(JavaClass javaClass) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(javaClass.getJavaPackage().getName()))
                .filter(Objects::nonNull)
                .flatMap(p -> p.getJavaClasses().stream())
                .filter(c -> javaClass.getName().equals(c.getName()))
                .findFirst()
                .orElse(javaClass);
    }

    private JavaMember findLatestMember(JavaMember javaMember) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(javaMember.getJavaClass().getJavaPackage().getName()))
                .filter(Objects::nonNull)
                .flatMap(p -> p.getJavaClasses().stream())
                .filter(c -> javaMember.getJavaClass().getName().equals(c.getName()))
                .filter(Objects::nonNull)
                .flatMap(c -> c.getJavaMembers().stream())
                .filter(m -> matches(m, javaMember))
                .findFirst()
                .orElse(javaMember);
    }

    private boolean matches(JavaMember javaMember, JavaMember toMatch) {
        assert javaMember.getJavaClass().getName().equals(toMatch.getJavaClass().getName());
        assert javaMember.getJavaClass().getJavaPackage().getName().equals(toMatch.getJavaClass().getJavaPackage().getName());

        return javaMember.getType() == toMatch.getType()
                && (javaMember.getOriginalSignature().equals(toMatch.getOriginalSignature()) || javaMember.getPrettifiedSignature().equals(toMatch.getPrettifiedSignature()));
    }
}
