package com.github.robtimus.whatsnewinjava.renderer;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
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
    private final LinkGenerator linkGenerator;

    public PageRenderer() {
        templateEngine = new TemplateEngine();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setSuffix(".html");
        templateEngine.addTemplateResolver(templateResolver);

        linkGenerator = new LinkGenerator();
    }

    public String renderNewPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {
        return renderPage(TEMPLATE_SPEC_NEW, createContext(JavaAPI.getNewPackagesPerVersion(javaAPIs, minimalJavaVersion)));
    }

    public String renderDeprecatedPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        return renderPage(TEMPLATE_SPEC_DEPRECATED, createContext(JavaAPI.getDeprecatedPackagesPerVersion(javaAPIs)));
    }

    public String renderRemovedPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        return renderPage(TEMPLATE_SPEC_REMOVED_, createContext(JavaAPI.getRemovedPackagesPerVersion(javaAPIs)));
    }

    private IContext createContext(Map<JavaVersion, Collection<JavaPackage>> packagesPerVersion) {
        Context context = new Context();
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
