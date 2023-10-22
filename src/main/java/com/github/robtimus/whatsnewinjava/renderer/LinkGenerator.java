/*
 * LinkGenerator.java
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
        latestBaseURL = javaAPIs.isEmpty() || omitLatestBaseURL ? "" : javaAPIs.lastEntry().getValue().javadoc().baseURL();
    }

    public String getLatestBaseURL() {
        return latestBaseURL;
    }

    public String generateLink(PageModule pageModule) {
        return generateModuleLink(pageModule.name());
    }

    public String generateNewModuleLink(PagePackage pagePackage) {
        return generateModuleLink(pagePackage.newModuleName());
    }

    private String generateModuleLink(String moduleName) {
        JavaModule latestModule = findLatestModule(moduleName);
        Javadoc javadoc = latestModule.javaAPI().javadoc();
        String format = javadoc.useModules() ? "%s%s/module-summary.html" : "%s%s-summary.html";
        return format.formatted(javadoc.baseURL(), moduleName)
                .replace(latestBaseURL, "");
    }

    public String generateLink(PagePackage pagePackage) {
        JavaPackage latestPackage = findLatestPackage(pagePackage);
        return "%s%s/package-summary.html".formatted(latestPackage.javaAPI().javadoc().baseURL(), getRelativeBaseURL(latestPackage))
                .replace(latestBaseURL, "");
    }

    public String generateLink(PageClass pageClass) {
        JavaClass latestClass = findLatestClass(pageClass);
        Javadoc javadoc = latestClass.javaAPI().javadoc();
        return "%s%s/%s.html".formatted(javadoc.baseURL(), getRelativeBaseURL(latestClass.javaPackage()), latestClass.name())
                .replace(latestBaseURL, "");
    }

    public String generateLink(PageMember pageMember) {
        JavaMember latestMember = findLatestMember(pageMember);
        JavaClass latestClass = latestMember.javaClass();
        Javadoc javadoc = latestClass.javaAPI().javadoc();
        return "%s%s/%s.html#%s".formatted(javadoc.baseURL(), getRelativeBaseURL(latestClass.javaPackage()), latestClass.name(), latestMember.originalSignature())
                .replace(latestBaseURL, "");
    }

    private String getRelativeBaseURL(JavaPackage javaPackage) {
        JavaModule javaModule = javaPackage.javaModule();
        String packagePath = javaPackage.name().replace('.', '/');

        return javaPackage.javaAPI().javadoc().useModules() ? javaModule.name() + "/" + packagePath : packagePath;
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
                .map(api -> api.findJavaPackage(pagePackage.name()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find package " + pagePackage));
    }

    private JavaClass findLatestClass(PageClass pageClass) {
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(pageClass.pagePackage().name()))
                .filter(Objects::nonNull)
                .flatMap(p -> p.javaClasses().stream())
                .filter(c -> pageClass.name().equals(c.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find class " + pageClass));
    }

    private JavaMember findLatestMember(PageMember pageMember) {
        PageClass pageClass = pageMember.pageClass();
        return javaAPIs.descendingMap().values().stream()
                .map(api -> api.findJavaPackage(pageClass.pagePackage().name()))
                .filter(Objects::nonNull)
                .flatMap(p -> p.javaClasses().stream())
                .filter(c -> pageClass.name().equals(c.name()))
                .filter(Objects::nonNull)
                .flatMap(c -> c.javaMembers().stream())
                .filter(m -> matches(m, pageMember))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find member " + pageMember + " of class " + pageClass));
    }

    private boolean matches(JavaMember javaMember, PageMember toMatch) {
        assert javaMember.javaClass().name().equals(toMatch.pageClass().name());
        assert javaMember.javaClass().javaPackage().name().equals(toMatch.pageClass().pagePackage().name());

        return javaMember.type() == toMatch.type() && javaMember.prettifiedSignature().equals(toMatch.signature());
    }
}
