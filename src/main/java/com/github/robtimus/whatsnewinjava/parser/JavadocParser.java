/*
 * JavadocParser.java
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

package com.github.robtimus.whatsnewinjava.parser;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.io.function.IOConsumer;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;
import com.github.robtimus.whatsnewinjava.parser.model.JavaClass;
import com.github.robtimus.whatsnewinjava.parser.model.JavaInterfaceList;
import com.github.robtimus.whatsnewinjava.parser.model.JavaMember;
import com.github.robtimus.whatsnewinjava.parser.model.JavaModule;
import com.github.robtimus.whatsnewinjava.parser.model.JavaVersion;
import com.github.robtimus.whatsnewinjava.parser.model.Javadoc;

@SuppressWarnings({ "nls", "javadoc" })
public final class JavadocParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavadocParser.class);

    public JavaAPI parseJavadoc(Path rootFolder, Set<String> packagesToIgnore, String javadocBaseURL, int javaVersion) throws IOException {
        JavaAPI javaAPI;
        if (Files.isDirectory(rootFolder.resolve("java.base"))) {
            javaAPI = new JavaAPI(new Javadoc(javadocBaseURL, true));
            // use modules structure
            try (Stream<Path> stream = Files.walk(rootFolder, 1)) {
                stream
                        .filter(p -> Files.isDirectory(p) && Files.isRegularFile(p.resolve("module-summary.html")))
                        .forEach(IOConsumer.unchecked(subFolder -> {
                            String moduleName = subFolder.getFileName().toString();
                            Parser parser = new Parser(subFolder, packagesToIgnore, javaVersion, moduleName, javaAPI);
                            parser.parse();
                        }));
            }
        } else {
            javaAPI = new JavaAPI(new Javadoc(javadocBaseURL, false));
            Parser parser = new Parser(rootFolder, packagesToIgnore, javaVersion, null, javaAPI);
            parser.parse();
        }

        LOGGER.info("Processed all packages");

        return javaAPI;
    }

    private static final class Parser {

        private static final Pattern COMMA_SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");
        private static final Pattern SINCE_PATTERN = Pattern.compile("^(?:(?:JDK|J2SE|JSE)\\s*)?([\\d.u]+)$");
        private static final Pattern INTERFACE_TITLE_PATTERN = Pattern.compile("(class|enum|interface|annotation) in (.*)");

        private final Path rootFolder;
        private final Set<String> packagesToIgnore;
        private final int javaVersion;

        private final String moduleName;
        private final JavaAPI javaAPI;

        private final Map<String, String> packageNamesToModuleNames = new HashMap<>();

        private Parser(Path rootFolder, Set<String> packagesToIgnore, int javaVersion, String moduleName, JavaAPI javaAPI) {
            this.rootFolder = rootFolder;
            this.packagesToIgnore = packagesToIgnore;
            this.javaVersion = javaVersion;
            this.moduleName = moduleName;
            this.javaAPI = javaAPI;
        }

        private JavaModule getJavaModule(String packageName) {
            if (moduleName != null) {
                return javaAPI.getJavaModule(moduleName);
            }
            if (packageNamesToModuleNames.isEmpty()) {
                return javaAPI.getAutomaticJavaModule();
            }
            String moduleNameForPackage = packageNamesToModuleNames.get(packageName);
            if (moduleNameForPackage == null) {
                throw new IllegalStateException("Could not find module for package " + packageName);
            }
            return javaAPI.getJavaModule(moduleNameForPackage);
        }

        private void parse() throws IOException {
            packageNamesToModuleNames.clear();

            if (moduleName != null) {
                Path moduleFile = rootFolder.resolve("module-summary.html");
                parseModuleFile(moduleFile);
            } else {
                try (Stream<Path> stream = Files.walk(rootFolder, 1)) {
                    stream
                            .filter(Files::isRegularFile)
                            .filter(this::isModuleFile)
                            .forEach(this::parseModuleFile);
                }

                if (packageNamesToModuleNames.isEmpty()) {
                    javaAPI.addAutomaticJavaModule();
                }
            }

            try (Stream<Path> stream = Files.walk(rootFolder)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(this::isNonIgnoredPackageSummaryFile)
                        .forEach(this::handlePackageFile);
            }
        }

        private void parseModuleFile(Path file) {
            String moduleToUse = moduleName != null ? moduleName : file.getFileName().toString().replace("-summary.html", "");

            LOGGER.info("Processing module {}", moduleToUse);

            LOGGER.debug("Parsing module file {}", file);

            try (InputStream input = Files.newInputStream(file)) {
                Document document = Jsoup.parse(input, "UTF-8", "");
                JavaVersion since = null;
                boolean deprecated = false;

                Element sinceTagElement = moduleSinceTagElement(document);
                if (sinceTagElement != null) {
                    String sinceString = extractSinceString(sinceTagElement);
                    since = extractJavaVersion(sinceString);
                    if (since == null) {
                        LOGGER.warn("Module {}: unexpected since: {}", moduleToUse, sinceString);
                    }
                }

                Element deprecatedBlockElement = moduleDeprecatedBlockElement(document);
                deprecated = deprecatedBlockElement != null;

                if (moduleName != null) {
                    javaAPI.addJavaModule(moduleName, since, deprecated);
                } else {
                    String moduleNameFromFile = file.getFileName().toString().replace("-summary.html", "");
                    Elements modulePackageLinks = modulePackageLinks(document);
                    for (Element modulePackageLink : modulePackageLinks) {
                        if (modulePackageLink.attr("href").endsWith("/package-summary.html")) {
                            String packageName = modulePackageLink.text();
                            if (packageNamesToModuleNames.containsKey(packageName)) {
                                throw new IllegalStateException("Duplicate package: " + packageName);
                            }
                            packageNamesToModuleNames.put(packageName, moduleNameFromFile);
                        }
                    }
                    javaAPI.addJavaModule(moduleNameFromFile, since, deprecated);
                }

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private Element moduleSinceTagElement(Document document) {
            // No Java 7/8 equivalent
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > dl > dt > span:contains(Since:)");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.moduleTags > dl > dt > span:contains(Since:)");
            }
            return document.selectFirst("main[role='main'] > section.module-description > dl > dt:contains(Since:)");
        }

        private Element moduleDeprecatedBlockElement(Document document) {
            // No Java 7/8 equivalent
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > div.deprecationBlock");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.moduleDescription > div.deprecationBlock");
            }
            return document.selectFirst("main[role='main'] > section.module-description > div.deprecationBlock");
        }

        private Elements modulePackageLinks(Document document) {
            // No Java 7/8 equivalent, but is this actually possible for Java 9+?
            return document.select("div.contentContainer table.packagesSummary:first-of-type tr > th > a");
        }

        private void handlePackageFile(Path file) {
            parsePackageFile(file);
            Path packageDir = requireNonNull(file.getParent());
            try {
                try (Stream<Path> stream = Files.walk(packageDir, 1)) {
                    stream
                            .filter(Files::isRegularFile)
                            .filter(this::isClassFile)
                            .forEach(this::handleClassFile);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void parsePackageFile(Path file) {
            String packageName = extractPackageName(file);
            LOGGER.info("Processing package {}", packageName);

            LOGGER.debug("Parsing package file {}", file);

            try (InputStream input = Files.newInputStream(file)) {
                Document document = Jsoup.parse(input, "UTF-8", "");
                JavaVersion since = null;
                boolean deprecated = false;

                Element sinceTagElement = packageSinceTagElement(document);
                String sinceString = null;
                if (sinceTagElement != null) {
                    sinceString = extractSinceString(sinceTagElement);
                } else if (javaVersion <= 8) {
                    // some packages in Java 7 and 8 have a differently reported since
                    Element sinceElement = document.selectFirst("div.contentContainer > div.block > ul > li:contains(Since )");
                    if (sinceElement != null) {
                        sinceString = sinceElement.text().replaceAll(".*Since\\s+", "");
                    }
                }
                if (sinceString != null) {
                    since = extractJavaVersion(sinceString);
                    if (since == null) {
                        LOGGER.warn("Package {}: unexpected since: {}", packageName, sinceString);
                    }
                }

                Element deprecatedBlockElement = packageDeprecatedBlockElement(document);
                deprecated = deprecatedBlockElement != null;

                getJavaModule(packageName)
                        .addJavaPackage(packageName, since, deprecated);

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private Element packageSinceTagElement(Document document) {
            if (javaVersion <= 8) {
                return document.selectFirst("div.contentContainer dl > dt > :contains(Since:)");
            }
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > dl > dt > span:contains(Since:)");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.packageDescription > dl > dt > span:contains(Since:)");
            }
            return document.selectFirst("main[role='main'] > section.package-description > dl > dt:contains(Since:)");
        }

        private Element packageDeprecatedBlockElement(Document document) {
            // No Java 7/8 equivalent
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > div.deprecationBlock");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.packageDescription > div.deprecationBlock");
            }
            return document.selectFirst("main[role='main'] > section.package-description > div.deprecation-block");
        }

        private void handleClassFile(Path file) {
            parseClassFile(file);
        }

        private void parseClassFile(Path file) {
            LOGGER.debug("Parsing class file {}", file);

            String packageName = extractPackageName(file);
            String className = extractClassName(file);

            try (InputStream input = Files.newInputStream(file)) {
                Document document = Jsoup.parse(input, "UTF-8", "");
                parseClassInfo(document, packageName, className);
                parseFields(document, packageName, className);
                parseConstructors(document, packageName, className);
                parseMethods(document, packageName, className);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void parseClassInfo(Document document, String packageName, String className) {
            JavaClass.Type type = classType(document);
            String superClass = classSuperClass(document);
            JavaInterfaceList interfaceList = classInterfaceList(document, type);

            if (superClass == null && !type.isInterface() && !("java.lang".equals(packageName) && "Object".equals(className))) {
                throw new IllegalStateException("Could not find super class for class " + packageName + "." + className);
            }

            JavaVersion since = null;
            boolean deprecated = false;

            Element sinceTagElement = classSinceTagElement(document);
            if (sinceTagElement != null) {
                String sinceString = extractSinceString(sinceTagElement);
                since = extractJavaVersion(sinceString);
                if (since == null) {
                    LOGGER.warn("Class {}.{}: unexpected since: {}", packageName, className, sinceString);
                }
            }

            Element deprecatedBlockElement = classDeprecatedBlockElement(document);
            deprecated = deprecatedBlockElement != null;

            Set<String> inheritedMethodSignatures = inheritedMethodSignatures(document);

            getJavaModule(packageName)
                    .getJavaPackage(packageName)
                    .addJavaClass(className, type, superClass, interfaceList, inheritedMethodSignatures, since, deprecated);
        }

        private JavaClass.Type classType(Document document) {
            Element headerElement = classHeaderElement(document);
            String type = headerElement.text().replaceAll("\\s.*", "").toUpperCase();
            return JavaClass.Type.valueOf(type);
        }

        private Element classHeaderElement(Document document) {
            if (javaVersion <= 12) {
                return document.selectFirst("div.header h2.title");
            }
            return document.selectFirst("div.header h1.title");
        }

        private String classSuperClass(Document document) {
            Element superClassElement = classSuperClassElement(document);
            return superClassElement == null ? null : superClassElement.text();
        }

        private Element classSuperClassElement(Document document) {
            if (javaVersion <= 12) {
                return document.select("div.contentContainer ul.inheritance > li > a").last();
            }
            if (javaVersion <= 14) {
                return document.select("div.contentContainer div.inheritance > a").last();
            }
            return document.select("main[role='main'] div.inheritance > a").last();
        }

        private JavaInterfaceList classInterfaceList(Document document, JavaClass.Type type) {
            String label = classInterfaceListLabel(type);
            String text = classInterfaceListNodes(document, label).stream()
                    .map(this::extractInterfaceListText)
                    .collect(joining());
            if (text.isEmpty()) {
                return JavaInterfaceList.EMPTY;
            }
            Set<String> interfaceNames = COMMA_SPLIT_PATTERN.splitAsStream(text)
                    .collect(Collector.of(InterfaceListCollector::new, InterfaceListCollector::add, InterfaceListCollector::combine, InterfaceListCollector::finish));
            return new JavaInterfaceList(interfaceNames);
        }

        private String classInterfaceListLabel(JavaClass.Type type) {
            switch (type) {
                case CLASS:
                case ENUM:
                case RECORD:
                    return "All Implemented Interfaces:";
                case ANNOTATION:
                case INTERFACE:
                    return "All Superinterfaces:";
                default:
                    throw new IllegalArgumentException("Unsupported Java class type: " + type);
            }
        }

        private List<Node> classInterfaceListNodes(Document document, String label) {
            if (javaVersion <= 12) {
                Element labelElement = document.selectFirst("div.contentContainer > div.description dl > dt:contains(" + label + ")");
                return labelElement == null ? emptyList() : labelElement.nextElementSibling().childNodes();
            }
            if (javaVersion <= 14) {
                Element labelElement = document.selectFirst("div.contentContainer > section.description dl > dt:contains(" + label + ")");
                return labelElement == null ? emptyList() : labelElement.nextElementSibling().childNodes();
            }
            if (javaVersion <= 16) {
                Element labelElement = document.selectFirst("main[role='main'] > section.description dl > dt:contains(" + label + ")");
                return labelElement == null ? emptyList() : labelElement.nextElementSibling().childNodes();
            }
            Element labelElement = document.selectFirst("main[role='main'] > section.class-description dl > dt:contains(" + label + ")");
            return labelElement == null ? emptyList() : labelElement.nextElementSibling().childNodes();
        }

        private String extractInterfaceListText(Node node) {
            if (node instanceof Element) {
                Element link = ((Element) node).selectFirst("a");
                String title = link.attr("title");
                Matcher matcher = title == null ? null : INTERFACE_TITLE_PATTERN.matcher(title);
                if (matcher == null || !matcher.matches()) {
                    throw new IllegalStateException("Missing or unexpected title: " + title);
                }
                return matcher.group(2) + "." + ((Element) node).text();
            }
            if (node instanceof TextNode) {
                return ((TextNode) node).text();
            }
            throw new IllegalStateException("Unexpected node type: " + node.getClass());
        }

        private Element classSinceTagElement(Document document) {
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > div.description dl > dt > span:contains(Since:)");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.description dl > dt > span:contains(Since:)");
            }
            if (javaVersion <= 16) {
                return document.selectFirst("main[role='main'] > section.description dl > dt:contains(Since:)");
            }
            return document.selectFirst("main[role='main'] > section.class-description dl > dt:contains(Since:)");
        }

        private Element classDeprecatedBlockElement(Document document) {
            if (javaVersion == 7) {
                return document.selectFirst("div.contentContainer > div.description > ul.blockList > li.blockList > div > strong:contains(Deprecated)");
            }
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > div.description > ul.blockList > li.blockList > div > span.deprecatedLabel");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.description > div.deprecationBlock");
            }
            if (javaVersion <= 16) {
                return document.selectFirst("main[role='main'] > section.description > div.deprecation-block");
            }
            return document.selectFirst("main[role='main'] > section.class-description > div.deprecation-block");
        }

        private Elements classInheritedMethodsElements(Document document) {
            if (javaVersion <= 12) {
                Elements elements = document.select("div.contentContainer > div.summary h3:contains(Methods declared in)");
                if (elements.isEmpty()) {
                    elements = document.select("div.contentContainer > div.summary h3:contains(Methods inherited from)");
                }
                return elements;
            }
            if (javaVersion <= 14) {
                return document.select("div.contentContainer > section.summary h3:contains(Methods declared in)");
            }
            return document.select("main[role='main'] > section.summary h3:contains(Methods declared in)");
        }

        private Element classMethodLinkParent(Element inheritedMethodsElement) {
            Element candidate = inheritedMethodsElement.nextElementSibling();
            while (candidate != null && !"code".equalsIgnoreCase(candidate.tagName())) {
                candidate = candidate.nextElementSibling();
            }
            return candidate;
        }

        private Set<String> inheritedMethodSignatures(Document document) {
            Set<String> signatures = new TreeSet<>();
            Elements elements = classInheritedMethodsElements(document);
            for (Element element : elements) {
                Element methodLinkParent = classMethodLinkParent(element);
                // methodLinkParent can be null if no methods are inherited
                if (methodLinkParent != null) {
                    Elements methodLinks = methodLinkParent.select("a");
                    for (Element methodLink : methodLinks) {
                        String href = methodLink.attr("href");
                        try {
                            String signature = URLDecoder.decode(href.replaceAll(".*#", ""), "UTF-8");
                            signatures.add(signature);
                        } catch (UnsupportedEncodingException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
            }
            return signatures;
        }

        private void parseFields(Document document, String packageName, String className) {
            parseMembers(document, packageName, className, JavaMember.Type.FIELD, "Field");
        }

        private void parseConstructors(Document document, String packageName, String className) {
            parseMembers(document, packageName, className, JavaMember.Type.CONSTRUCTOR, "Constructor");
        }

        private void parseMethods(Document document, String packageName, String className) {
            parseMembers(document, packageName, className, JavaMember.Type.METHOD, "Method");
        }

        private void parseMembers(Document document, String packageName, String className, JavaMember.Type memberType, String memberTypeString) {
            Element memberDetailElement = memberDetailElement(document, memberTypeString);
            if (memberDetailElement == null) {
                LOGGER.debug("No members of type {} defined for class {}.{}", memberTypeString, packageName, className);
                return;
            }
            // go one level up to find the <ul> with the members
            Elements memberElements = memberElements(memberDetailElement);
            for (Element memberElement : memberElements) {
                String signature = signature(memberElement);
                JavaVersion since = null;
                boolean deprecated = false;

                Element sinceTagElement = memberSinceTagElement(memberElement);
                if (sinceTagElement != null) {
                    String sinceString = extractSinceString(sinceTagElement);
                    since = extractJavaVersion(sinceString);
                    if (since == null) {
                        LOGGER.warn("Class {}.{}, member {}: unexpected since: {}", packageName, className, signature, sinceString);
                    }
                }

                Element deprecatedBlockElement = memberDeprecatedBlockElement(memberElement);
                deprecated = deprecatedBlockElement != null;

                getJavaModule(packageName)
                        .getJavaPackage(packageName)
                        .getJavaClass(className)
                        .addJavaMember(memberType, signature, since, deprecated);
            }
        }

        private Element memberDetailElement(Document document, String memberType) {
            if (javaVersion == 7 || javaVersion == 8) {
                Element element = document.selectFirst("div.contentContainer > div.details h3:contains(" + memberType + " Detail)");
                if (element == null) {
                    element = document.selectFirst("div.details h3:contains(" + memberType + " Detail)");
                }
                return element;
            }
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > div.details h3:contains(" + memberType + " Detail)");
            }
            if (javaVersion <= 14) {
                return document.selectFirst("div.contentContainer > section.details h2:contains(" + memberType + " Detail)");
            }
            return document.selectFirst("main[role='main'] > section.details h2:contains(" + memberType + " Detail)");
        }

        private Elements memberElements(Element memberDetailElement) {
            if (javaVersion == 7) {
                // go one level up to find the <ul> with the members
                // also, add any of these inside a code element (e.g. in ByteArrayOutputStream)
                Elements memberElements = memberDetailElement.parent().select("> ul");
                memberElements.addAll(memberDetailElement.parent().select("code > ul"));
                return memberElements;
            }
            if (javaVersion <= 12) {
                // go one level up to find the <ul> with the members
                return memberDetailElement.parent().select("> ul");
            }
            // go one level up to find the sections with the members
            return memberDetailElement.parent().select("> ul > li > section.detail");
        }

        private String signature(Element memberElement) {
            if (javaVersion <= 12) {
                Element signatureElement = memberElement.previousElementSibling();
                String id = signatureElement.attr("id");
                String name = signatureElement.attr("name");
                return id.isEmpty() ? name : id;
            }
            if (javaVersion <= 14) {
                // sometimes multiple anchors exist,
                // e.g. Comparable.compareTo(java.lang.Object) and Comparable.compareTo(T)
                // take the last one
                return memberElement.select("h3 > a").last().attr("id");
            }
            return memberElement.attr("id");
        }

        private Element memberSinceTagElement(Element memberElement) {
            if (javaVersion <= 12) {
                return memberElement.selectFirst("li dl > dt > span:contains(Since:)");
            }
            if (javaVersion <= 14) {
                return memberElement.selectFirst("dl > dt > span:contains(Since:)");
            }
            return memberElement.selectFirst("dl > dt:contains(Since:)");
        }

        private Element memberDeprecatedBlockElement(Element memberElement) {
            if (javaVersion == 7) {
                return memberElement.selectFirst("li > div > span.strong:contains(Deprecated)");
            }
            if (javaVersion <= 12) {
                return memberElement.selectFirst("li > div > span.deprecatedLabel");
            }
            if (javaVersion <= 14) {
                return memberElement.selectFirst("div.deprecationBlock > span.deprecatedLabel");
            }
            return memberElement.selectFirst("div.deprecation-block > span.deprecated-label");
        }

        private String extractPackageName(Path file) {
            Path parent = requireNonNull(file.getParent());
            return rootFolder.relativize(parent).toString().replace('\\', '/').replace('/', '.');
        }

        private String extractClassName(Path file) {
            Path fileName = requireNonNull(file.getFileName());
            return fileName.toString().replaceAll("\\.html$", "");
        }

        private String extractSinceString(Element sinceTagElement) {
            // Java 14 and before: the since tag element is the span inside the dt
            // Java 15: the since tag element is the dt itself
            // We want the dt's sibling (the dd)
            Element sinceValueElement = "span".equalsIgnoreCase(sinceTagElement.tagName())
                    ? sinceTagElement.parent().nextElementSibling()
                    : sinceTagElement.nextElementSibling();
            return sinceValueElement.text();
        }

        private JavaVersion extractJavaVersion(String since) {
            return COMMA_SPLIT_PATTERN.splitAsStream(since)
                    .map(this::safeExtractJavaVersion)
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElse(null);
        }

        private JavaVersion safeExtractJavaVersion(String since) {
            Matcher matcher = SINCE_PATTERN.matcher(since);
            return matcher.matches() ? JavaVersion.parse(matcher.group(1)) : null;
        }

        private boolean isNonIgnoredPackageSummaryFile(Path file) {
            Path fileName = file.getFileName();
            return fileName != null && "package-summary.html".equals(fileName.toString()) && isNonIgnoredPackageFile(file);
        }

        private boolean isNonIgnoredPackageFile(Path file) {
            String packageName = extractPackageName(file);

            for (String packageToIgnore : packagesToIgnore) {
                if (packageName.equals(packageToIgnore) || packageName.startsWith(packageToIgnore + ".")) {
                    return false;
                }
            }

            return true;
        }

        private boolean isClassFile(Path file) {
            Path fileName = file.getFileName();
            return fileName != null && isClassFile(fileName.toString());
        }

        private boolean isClassFile(String fileName) {
            return !fileName.startsWith("package-") && !fileName.contains("-package-");
        }

        private boolean isModuleFile(Path file) {
            Path fileName = file.getFileName();
            return fileName != null && isModuleFile(fileName.toString());
        }

        private boolean isModuleFile(String fileName) {
            return fileName.endsWith("-summary.html")
                    && !"overview-summary.html".equals(fileName)
                    && !"package-summary.html".equals(fileName)
                    && !fileName.contains("-package-")
                    && !fileName.matches("compact\\d+-summary\\.html");
        }
    }

    private static final class InterfaceListCollector {

        private final List<String> interfaceNames = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();

        private void add(String part) {
            if (current.length() == 0) {
                // no previously opened generic type list
                if (part.indexOf('<') == -1 || part.indexOf('>') != -1) {
                    // no new generic type list, or one that is immediately ended; just add the part
                    interfaceNames.add(part);
                } else {
                    // a new generic type list that is not yet ended
                    current.append(part);
                }
            } else {
                // a previously opened generic type list; append the current part anyway
                current.append(',').append(part);
                if (part.indexOf('>') != -1) {
                    // end the current opened generic type list
                    interfaceNames.add(current.toString());
                    current.delete(0, current.length());
                }
            }
        }

        private InterfaceListCollector combine(@SuppressWarnings("unused") InterfaceListCollector collector) {
            throw new IllegalStateException("No parallel collecting supported");
        }

        private Set<String> finish() {
            if (current.length() > 0) {
                throw new IllegalStateException("Contains an opened generic type list: " + current);
            }
            return new LinkedHashSet<>(interfaceNames);
        }
    }
}
