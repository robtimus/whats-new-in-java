/*
 * PageRenderer.java
 * Copyright 2018 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

@SuppressWarnings({ "nls", "javadoc" })
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
