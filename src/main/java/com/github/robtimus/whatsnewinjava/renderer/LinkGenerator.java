package com.github.robtimus.whatsnewinjava.renderer;

import com.github.robtimus.whatsnewinjava.domain.JavaClass;
import com.github.robtimus.whatsnewinjava.domain.JavaMember;
import com.github.robtimus.whatsnewinjava.domain.JavaModule;
import com.github.robtimus.whatsnewinjava.domain.JavaPackage;

public final class LinkGenerator {

    public String generateLink(JavaModule javaModule) {
        String format = javaModule.getJavadoc().useModules() ? "%s%s/module-summary.html" : "%s%s-summary.html";
        return String.format(format, javaModule.getJavadoc().getBaseURL(), javaModule.getName());
    }

    public String generateLink(JavaPackage javaPackage) {
        return String.format("%s%s/package-summary.html", javaPackage.getJavadoc().getBaseURL(), getRelativeBaseURL(javaPackage));
    }

    public String generateLink(JavaClass javaClass) {
        return String.format("%s%s/%s.html", javaClass.getJavadoc().getBaseURL(), getRelativeBaseURL(javaClass.getJavaPackage()), javaClass.getName());
    }

    public String generateLink(JavaMember javaMember) {
        return generateLink(javaMember.getJavaClass()) + "#" + javaMember.getSignature();
    }

    private String getRelativeBaseURL(JavaPackage javaPackage) {
        JavaModule javaModule = javaPackage.getJavaModule();
        String packagePath = javaPackage.getName().replace('.', '/');

        return javaPackage.getJavadoc().useModules() ? javaModule.getName() + "/" + packagePath : packagePath;
    }
}
