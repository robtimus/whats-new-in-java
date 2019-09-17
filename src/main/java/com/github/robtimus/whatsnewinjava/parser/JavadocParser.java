package com.github.robtimus.whatsnewinjava.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.io.function.IOConsumer;
import com.github.robtimus.whatsnewinjava.domain.JavaAPI;
import com.github.robtimus.whatsnewinjava.domain.JavaMember;
import com.github.robtimus.whatsnewinjava.domain.JavaVersion;
import com.github.robtimus.whatsnewinjava.domain.Javadoc;

public final class JavadocParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavadocParser.class);

    public JavaAPI parseJavadoc(Path rootFolder, Set<String> packagesToIgnore, String javadocBaseURL, int javaVersion) throws IOException {
        JavaAPI javaAPI;
        if (Files.isDirectory(rootFolder.resolve("java.base"))) {
            javaAPI = new JavaAPI(new Javadoc(javadocBaseURL, true));
            // use modules structure
            Files.walk(rootFolder, 1)
                    .filter(p -> Files.isDirectory(p) && Files.isRegularFile(p.resolve("module-summary.html")))
                    .forEach(IOConsumer.unchecked(subFolder -> {
                        String moduleName = subFolder.getFileName().toString();
                        Parser parser = new Parser(subFolder, packagesToIgnore, javaVersion, moduleName, javaAPI);
                        parser.parse();
                    }));
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

        private String getModuleName(String packageName) {
            if (moduleName != null) {
                return moduleName;
            }
            if (packageNamesToModuleNames.isEmpty()) {
                return null;
            }
            String moduleNameForPackage = packageNamesToModuleNames.get(packageName);
            if (moduleNameForPackage == null) {
                throw new IllegalStateException("Could not find module for package " + packageName);
            }
            return moduleNameForPackage;
        }

        private void parse() throws IOException {
            packageNamesToModuleNames.clear();

            if (moduleName != null) {
                Path moduleFile = rootFolder.resolve("module-summary.html");
                parseModuleFile(moduleFile);
            } else {
                Files.walk(rootFolder, 1)
                        .filter(Files::isRegularFile)
                        .filter(this::isModuleFile)
                        .forEach(this::parseModuleFile);

                if (packageNamesToModuleNames.isEmpty()) {
                    javaAPI.addModule(null, null, false);
                }
            }

            Files.walk(rootFolder)
                    .filter(Files::isRegularFile)
                    .filter(this::isNonIgnoredPackageSummaryFile)
                    .forEach(this::handlePackageFile);
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
                    javaAPI.addModule(moduleName, since, deprecated);
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
                    javaAPI.addModule(moduleNameFromFile, since, deprecated);
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
            return document.selectFirst("div.contentContainer > section.moduleTags > dl > dt > span:contains(Since:)");
        }

        private Element moduleDeprecatedBlockElement(Document document) {
            // No Java 7/8 equivalent
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > div.deprecationBlock");
            }
            return document.selectFirst("div.contentContainer > section.moduleDescription > div.deprecationBlock");
        }

        private Elements modulePackageLinks(Document document) {
            // No Java 7/8 equivalent, but is this actually possible for Java 9+?
            return document.select("div.contentContainer table.packagesSummary:first-of-type tr > th > a");
        }

        private void handlePackageFile(Path file) {
            parsePackageFile(file);
            Path packageDir = Objects.requireNonNull(file.getParent());
            try {
                Files.walk(packageDir, 1)
                        .filter(Files::isRegularFile)
                        .filter(this::isClassFile)
                        .forEach(this::handleClassFile);
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
                if (sinceTagElement != null) {
                    String sinceString = extractSinceString(sinceTagElement);
                    since = extractJavaVersion(sinceString);
                    if (since == null) {
                        LOGGER.warn("Package {}: unexpected since: {}", packageName, sinceString);
                    }
                }

                Element deprecatedBlockElement = packageDeprecatedBlockElement(document);
                deprecated = deprecatedBlockElement != null;

                javaAPI.addPackage(getModuleName(packageName), packageName, since, deprecated);

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private Element packageSinceTagElement(Document document) {
            // No Java 7/8 equivalent
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > dl > dt > span:contains(Since:)");
            }
            return document.selectFirst("div.contentContainer > section.packageDescription > dl > dt > span:contains(Since:)");
        }

        private Element packageDeprecatedBlockElement(Document document) {
            // No Java 7/8 equivalent
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > section[role=region] > div.deprecationBlock");
            }
            return document.selectFirst("div.contentContainer > section.packageDescription > div.deprecationBlock");
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

            javaAPI.addClass(getModuleName(packageName), packageName, className, since, deprecated, inheritedMethodSignatures);
        }

        private Element classSinceTagElement(Document document) {
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > div.description dl > dt > span:contains(Since:)");
            }
            return document.selectFirst("div.contentContainer > section.description dl > dt > span:contains(Since:)");
        }

        private Element classDeprecatedBlockElement(Document document) {
            if (javaVersion == 7) {
                return document.selectFirst("div.contentContainer > div.description > ul.blockList > li.blockList > div > strong:contains(Deprecated)");
            }
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > div.description > ul.blockList > li.blockList > div > span.deprecatedLabel");
            }
            return document.selectFirst("div.contentContainer > section.description > div.deprecationBlock");
        }

        private Elements classInheritedMethodsElements(Document document) {
            if (javaVersion <= 12) {
                Elements elements = document.select("div.contentContainer > div.summary h3:contains(Methods declared in)");
                if (elements.size() == 0) {
                    elements = document.select("div.contentContainer > div.summary h3:contains(Methods inherited from)");
                }
                return elements;
            }
            return document.select("div.contentContainer > section.summary h3:contains(Methods declared in)");
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

                javaAPI.addMember(getModuleName(packageName), packageName, className, memberType, signature, since, deprecated);
            }
        }

        private Element memberDetailElement(Document document, String memberType) {
            if (javaVersion <= 12) {
                return document.selectFirst("div.contentContainer > div.details h3:contains(" + memberType + " Detail)");
            }
            return document.selectFirst("div.contentContainer > section.details h2:contains(" + memberType + " Detail)");
        }

        private Elements memberElements(Element memberDetailElement) {
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
            // sometimes multiple anchors exist,
            // e.g. Comparable.compareTo(java.lang.Object) and Comparable.compareTo(T)
            // take the last one
            return memberElement.select("h3 > a").last().attr("id");
        }

        private Element memberSinceTagElement(Element memberElement) {
            if (javaVersion <= 12) {
                return memberElement.selectFirst("li dl > dt > span:contains(Since:)");
            }
            return memberElement.selectFirst("dl > dt > span:contains(Since:)");
        }

        private Element memberDeprecatedBlockElement(Element memberElement) {
            if (javaVersion == 7) {
                return memberElement.selectFirst("li > div > span.strong:contains(Deprecated)");
            }
            if (javaVersion <= 12) {
                return memberElement.selectFirst("li > div > span.deprecatedLabel");
            }
            return memberElement.selectFirst("div.deprecationBlock > span.deprecatedLabel");
        }

        private String extractPackageName(Path file) {
            Path parent = Objects.requireNonNull(file.getParent());
            return rootFolder.relativize(parent).toString().replace('\\', '/').replace('/', '.');
        }

        private String extractClassName(Path file) {
            Path fileName = Objects.requireNonNull(file.getFileName());
            return fileName.toString().replaceAll("\\.html$", "");
        }

        private String extractSinceString(Element sinceTagElement) {
            // the since tag element is the span inside the dt; we want the dt's sibling (the dd)
            Element sinceValueElement = sinceTagElement.parent().nextElementSibling();
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
}
