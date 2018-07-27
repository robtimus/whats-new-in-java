package com.github.robtimus.whatsnewinjava.renderer;

import com.github.robtimus.whatsnewinjava.domain.JavaClass;
import com.github.robtimus.whatsnewinjava.domain.JavaMember;
import com.github.robtimus.whatsnewinjava.domain.JavaPackage;

public final class LinkGenerator {

    private final String javadocBaseURL;

    public LinkGenerator(String javadocBaseURL) {
        this.javadocBaseURL = javadocBaseURL;
    }

    public String generateLink(JavaPackage javaPackage) {
        return String.format("%s%s/package-summary.html", javadocBaseURL, getRelativeBaseURL(javaPackage));
    }

    public String generateLink(JavaClass javaClass) {
        return String.format("%s%s/%s.html", javadocBaseURL, getRelativeBaseURL(javaClass.getJavaPackage()), javaClass.getName());
    }

    public String generateLink(JavaMember javaMember) {
        return generateLink(javaMember.getJavaClass()) + "#" + javaMember.getSignature();
    }

    private String getRelativeBaseURL(JavaPackage javaPackage) {
        return javaPackage.getName().replace('.', '/');
    }
}
