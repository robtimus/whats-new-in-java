package com.github.robtimus.whatsnewinjava.renderer;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;

public final class PageRenderer {

    private static final String PAGE_TEMPLATE = "page-template.html";

    private static final TemplateSpec TEMPLATE_SPEC = new TemplateSpec(PAGE_TEMPLATE, TemplateMode.HTML);

    private final TemplateEngine templateEngine;
    private final LinkGenerator linkGenerator;

    public PageRenderer(String javadocBaseURL) {
        templateEngine = new TemplateEngine();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setSuffix(".html");
        templateEngine.addTemplateResolver(templateResolver);

        linkGenerator = new LinkGenerator(javadocBaseURL);
    }

    public String renderPage(JavaAPI javaAPI) {
        String output = templateEngine.process(TEMPLATE_SPEC, createContext(javaAPI));
        // remove blank lines
        return output.replaceAll("\n(\\s+?\n)+", "\n");
    }

    private IContext createContext(JavaAPI javaAPI) {
        Context context = new Context();
        context.setVariable("packagesPerVersion", javaAPI.getPackagesPerVersion());
        context.setVariable("linkGenerator", linkGenerator);
        return context;
    }
}
