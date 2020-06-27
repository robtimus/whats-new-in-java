package com.github.robtimus.whatsnewinjava.renderer;

import static java.util.Collections.unmodifiableMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;
import com.github.robtimus.whatsnewinjava.parser.model.JavaVersion;
import com.github.robtimus.whatsnewinjava.renderer.model.PageModel;

public final class PageRenderer {

    private static final String PAGE_TEMPLATE_NEW = "page-template-new.html";
    private static final String PAGE_TEMPLATE_DEPRECATED = "page-template-deprecated.html";
    private static final String PAGE_TEMPLATE_REMOVED = "page-template-removed.html";

    private static final TemplateSpec TEMPLATE_SPEC_NEW = new TemplateSpec(PAGE_TEMPLATE_NEW, TemplateMode.HTML);
    private static final TemplateSpec TEMPLATE_SPEC_DEPRECATED = new TemplateSpec(PAGE_TEMPLATE_DEPRECATED, TemplateMode.HTML);
    private static final TemplateSpec TEMPLATE_SPEC_REMOVED_ = new TemplateSpec(PAGE_TEMPLATE_REMOVED, TemplateMode.HTML);

    private static final Map<JavaClass.Type, String> INTERFACE_PREFIXES;

    static {
        Map<JavaClass.Type, String> interfacePrefixes = new EnumMap<>(JavaClass.Type.class);
        for (JavaClass.Type type : JavaClass.Type.values()) {
            String prefix = type.isInterface() ? "extends" : "implements";
            interfacePrefixes.put(type, prefix);
        }
        INTERFACE_PREFIXES = unmodifiableMap(interfacePrefixes);
    }

    private final TemplateEngine templateEngine;

    public PageRenderer() {
        templateEngine = new TemplateEngine();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setSuffix(".html");
        templateEngine.addTemplateResolver(templateResolver);
    }

    public String renderNewPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs, JavaVersion minimalJavaVersion) {
        PageModel pageModel = PageModel.forNew(javaAPIs, minimalJavaVersion);
        LinkGenerator linkGenerator = new LinkGenerator(javaAPIs);
        return renderPage(TEMPLATE_SPEC_NEW, createContext(pageModel, linkGenerator));
    }

    public String renderDeprecatedPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        PageModel pageModel = PageModel.forDeprecated(javaAPIs);
        LinkGenerator linkGenerator = new LinkGenerator(javaAPIs);
        return renderPage(TEMPLATE_SPEC_DEPRECATED, createContext(pageModel, linkGenerator));
    }

    public String renderRemovedPage(NavigableMap<JavaVersion, JavaAPI> javaAPIs) {
        PageModel pageModel = PageModel.forRemoved(javaAPIs);
        // don't use the actual JavaAPIs here as the removal already links to the latest version that has the module / package / class / member
        LinkGenerator linkGenerator = new LinkGenerator(javaAPIs, true);
        return renderPage(TEMPLATE_SPEC_REMOVED_, createContext(pageModel, linkGenerator));
    }

    private IContext createContext(PageModel pageModel, LinkGenerator linkGenerator) {

        Context context = new Context();
        context.setVariable("pageModel", pageModel);
        context.setVariable("linkGenerator", linkGenerator);
        context.setVariable("interfacePrefixes", INTERFACE_PREFIXES);
        return context;
    }

    private String renderPage(TemplateSpec template, IContext context) {
        String output = templateEngine.process(template, context);
        // remove blank lines
        return output.replaceAll("\n(\\s+?\n)+", "\n");
    }
}
