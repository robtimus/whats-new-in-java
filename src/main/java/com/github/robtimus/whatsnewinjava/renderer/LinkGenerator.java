package com.github.robtimus.whatsnewinjava.renderer;

import java.util.NavigableMap;
import java.util.Objects;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;
import com.github.robtimus.whatsnewinjava.parser.model.JavaMember;
import com.github.robtimus.whatsnewinjava.parser.model.JavaModule;
import com.github.robtimus.whatsnewinjava.parser.model.JavaPackage;
import com.github.robtimus.whatsnewinjava.parser.model.JavaVersion;
import com.github.robtimus.whatsnewinjava.parser.model.Javadoc;
import com.github.robtimus.whatsnewinjava.renderer.model.PageClass;
import com.github.robtimus.whatsnewinjava.renderer.model.PageMember;
import com.github.robtimus.whatsnewinjava.renderer.model.PageModule;
import com.github.robtimus.whatsnewinjava.renderer.model.PagePackage;

@SuppressWarnings("nls")
final class LinkGenerator {

    private final NavigableMap<JavaVersion, JavaAPI> javaAPIs;
    private final String latestBaseURL;

    LinkGenerator(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        this(javaAPIs, false);
    }

    LinkGenerator(NavigableMap<JavaVersion, JavaAPI> javaAPIs, boolean omitLatestBaseURL) {
        this.javaAPIs = javaAPIs;
        latestBaseURL = javaAPIs.isEmpty() || omitLatestBaseURL ? "" : javaAPIs.lastEntry().getValue().getJavadoc().getBaseURL();
    }

    public String getLatestBaseURL() {
        return latestBaseURL;
    }

    public String generateLink(PageModule pageModule) {
        return generateModuleLink(pageModule.getName());
    }

    public String generateNewModuleLink(PagePackage pagePackage) {
        return generateModuleLink(pagePackage.getNewModuleName());
    }

    private String generateModuleLink(String moduleName) {
        JavaModule latestModule = findLatestModule(moduleName);
        Javadoc javadoc = latestModule.getJavaAPI().getJavadoc();
        String format = javadoc.useModules() ? "%s%s/module-summary.html" : "%s%s-summary.html";
        return String.format(format, javadoc.getBaseURL(), moduleName)
                .replace(latestBaseURL, "");
    }

    public String generateLink(PagePackage pagePackage) {
        JavaPackage latestPackage = findLatestPackage(pagePackage);
        return String.format("%s%s/package-summary.html", latestPackage.getJavaAPI().getJavadoc().getBaseURL(), getRelativeBaseURL(latestPackage))
                .replace(latestBaseURL, "");
    }

    public String generateLink(PageClass pageClass) {
        JavaClass latestClass = findLatestClass(pageClass);
        Javadoc javadoc = latestClass.getJavaAPI().getJavadoc();
        return String.format("%s%s/%s.html", javadoc.getBaseURL(), getRelativeBaseURL(latestClass.getJavaPackage()), latestClass.getName())
                .replace(latestBaseURL, "");
    }

    public String generateLink(PageMember pageMember) {
        JavaMember latestMember = findLatestMember(pageMember);
        JavaClass latestClass = latestMember.getJavaClass();
        Javadoc javadoc = latestClass.getJavaAPI().getJavadoc();
        return String.format("%s%s/%s.html#%s", javadoc.getBaseURL(), getRelativeBaseURL(latestClass.getJavaPackage()), latestClass.getName(), latestMember.getOriginalSignature())
                .replace(latestBaseURL, "");
    }

    private String getRelativeBaseURL(JavaPackage javaPackage) {
        JavaModule javaModule = javaPackage.getJavaModule();
        String packagePath = javaPackage.getName().replace('.', '/');

        return javaPackage.getJavaAPI().getJavadoc().useModules() ? javaModule.getName() + "/" + packagePath : packagePath;
    }

    private JavaModule findLatestModule(String moduleName) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaModule(moduleName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find module " + moduleName));
    }

    private JavaPackage findLatestPackage(PagePackage pagePackage) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(pagePackage.getName()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find package " + pagePackage));
    }

    private JavaClass findLatestClass(PageClass pageClass) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(pageClass.getPagePackage().getName()))
                .filter(Objects::nonNull)
                .flatMap(p -> p.getJavaClasses().stream())
                .filter(c -> pageClass.getName().equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find class " + pageClass));
    }

    private JavaMember findLatestMember(PageMember pageMember) {
        PageClass pageClass = pageMember.getPageClass();
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(pageClass.getPagePackage().getName()))
                .filter(Objects::nonNull)
                .flatMap(p -> p.getJavaClasses().stream())
                .filter(c -> pageClass.getName().equals(c.getName()))
                .filter(Objects::nonNull)
                .flatMap(c -> c.getJavaMembers().stream())
                .filter(m -> matches(m, pageMember))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find member " + pageMember + " of class " + pageClass));
    }

    private boolean matches(JavaMember javaMember, PageMember toMatch) {
        assert javaMember.getJavaClass().getName().equals(toMatch.getPageClass().getName());
        assert javaMember.getJavaClass().getJavaPackage().getName().equals(toMatch.getPageClass().getPagePackage().getName());

        return javaMember.getType() == toMatch.getType() && javaMember.getPrettifiedSignature().equals(toMatch.getSignature());
    }
}
