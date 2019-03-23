package com.github.robtimus.whatsnewinjava.renderer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.domain.JavaModule;
import com.github.robtimus.whatsnewinjava.domain.JavaPackage;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;

public final class PageRenderer {

    private static final String PAGE_TEMPLATE_NEW = "page-template-new.html";
    private static final String PAGE_TEMPLATE_DEPRECATED = "page-template-deprecated.html";
    private static final String PAGE_TEMPLATE_REMOVED = "page-template-removed.html";

    private static final TemplateSpec TEMPLATE_SPEC_NEW = new TemplateSpec(PAGE_TEMPLATE_NEW, TemplateMode.HTML);
    private static final TemplateSpec TEMPLATE_SPEC_DEPRECATED = new TemplateSpec(PAGE_TEMPLATE_DEPRECATED, TemplateMode.HTML);
    private static final TemplateSpec TEMPLATE_SPEC_REMOVED_ = new TemplateSpec(PAGE_TEMPLATE_REMOVED, TemplateMode.HTML);

    private final TemplateEngine templateEngine;

    public PageRenderer() {
        templateEngine = new TemplateEngine();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setSuffix(".html");
        templateEngine.addTemplateResolver(templateResolver);
    }

    public String renderNewPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = JavaAPI.getNewModulesPerVersion(javaAPIs, minimalJavaVersion);
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = JavaAPI.getNewPackagesPerVersion(javaAPIs, minimalJavaVersion);
        LinkGenerator linkGenerator = new LinkGenerator(javaAPIs);
        return renderPage(TEMPLATE_SPEC_NEW, createContext(modulesPerVersion, packagesPerVersion, linkGenerator));
    }

    public String renderDeprecatedPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = JavaAPI.getDeprecatedModulesPerVersion(javaAPIs);
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = JavaAPI.getDeprecatedPackagesPerVersion(javaAPIs);
        LinkGenerator linkGenerator = new LinkGenerator(javaAPIs);
        return renderPage(TEMPLATE_SPEC_DEPRECATED, createContext(modulesPerVersion, packagesPerVersion, linkGenerator));
    }

    public String renderRemovedPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        Map<JavaVersion, List<JavaModule>> modulesPerVersion = JavaAPI.getRemovedModulesPerVersion(javaAPIs);
        Map<JavaVersion, List<JavaPackage>> packagesPerVersion = JavaAPI.getRemovedPackagesPerVersion(javaAPIs);
        // don't use the actual JavaAPIs here as the removal already links to the latest version that has the module / package / class / member
        LinkGenerator linkGenerator = new LinkGenerator(Collections.emptyNavigableMap());
        return renderPage(TEMPLATE_SPEC_REMOVED_, createContext(modulesPerVersion, packagesPerVersion, linkGenerator));
    }

    private IContext createContext(Map<JavaVersion, List<JavaModule>> modulesPerVersion, Map<JavaVersion, List<JavaPackage>> packagesPerVersion,
            LinkGenerator linkGenerator) {

        Context context = new Context();
        context.setVariable("modulesPerVersion", modulesPerVersion);
        context.setVariable("packagesPerVersion", packagesPerVersion);
        context.setVariable("linkGenerator", linkGenerator);
        return context;
    }

    private String renderPage(TemplateSpec template, IContext context) {
        String output = templateEngine.process(template, context);
        // remove blank lines
        return output.replaceAll("\n(\\s+?\n)+", "\n");
    }
}
