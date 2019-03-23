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

    public JavaAPI parseJavadoc(Path rootFolder, Set<String> packagesToIgnore, String javadocBaseURL) throws IOException {
        JavaAPI javaAPI;
        if (Files.isDirectory(rootFolder.resolve("java.base"))) {
            javaAPI = new JavaAPI(new Javadoc(javadocBaseURL, true));
            // use modules structure
            Files.walk(rootFolder, 1)
                    .filter(p -> Files.isDirectory(p) && Files.isRegularFile(p.resolve("module-summary.html")))
                    .forEach(IOConsumer.unchecked(subFolder -> {
                        String moduleName = subFolder.getFileName().toString();
                        Parser parser = new Parser(subFolder, packagesToIgnore, moduleName, javaAPI);
                        parser.parse();
                    }));
        } else {
            javaAPI = new JavaAPI(new Javadoc(javadocBaseURL, false));
            Parser parser = new Parser(rootFolder, packagesToIgnore, null, javaAPI);
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

        private final String moduleName;
        private final JavaAPI javaAPI;

        private final Map<String, String> packageNamesToModuleNames = new HashMap<>();

        private Parser(Path rootFolder, Set<String> packagesToIgnore, String moduleName, JavaAPI javaAPI) {
            this.rootFolder = rootFolder;
            this.packagesToIgnore = packagesToIgnore;
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
            return document.selectFirst("div.contentContainer > section[role=region] > dl > dt > span:contains(Since:)");
        }

        private Element moduleDeprecatedBlockElement(Document document) {
            // No Java 7/8 equivalent
            return document.selectFirst("div.contentContainer > section[role=region] > div.deprecationBlock");
        }

        private Elements modulePackageLinks(Document document) {
            // No Java 7/8 equivalent
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
            return document.selectFirst("div.contentContainer > section[role=region] > dl > dt > span:contains(Since:)");
        }

        private Element packageDeprecatedBlockElement(Document document) {
            // No Java 7/8 equivalent
            return document.selectFirst("div.contentContainer > section[role=region] > div.deprecationBlock");
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
            return document.selectFirst("div.contentContainer > div.description dl > dt > span:contains(Since:)");
        }

        private Element classDeprecatedBlockElement(Document document) {
            Element element = document.selectFirst("div.contentContainer > div.description > ul.blockList > li.blockList > div > span.deprecatedLabel");
            if (element == null && javaAPI.getJavadoc().getBaseURL().contains("/7/")) {
                // Java 7 workaround
                element = document.selectFirst("div.contentContainer > div.description > ul.blockList > li.blockList > div > strong:contains(Deprecated)");
            }
            return element;
        }

        private Set<String> inheritedMethodSignatures(Document document) {
            Set<String> signatures = new TreeSet<>();
            Elements elements = document.select("div.contentContainer > div.summary h3:contains(Methods declared in)");
            if (elements.size() == 0) {
                elements = document.select("div.contentContainer > div.summary h3:contains(Methods inherited from)");
            }
            for (Element element : elements) {
                Element methodLinkParent = element.nextElementSibling();
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
            return document.selectFirst("div.contentContainer > div.details h3:contains(" + memberType + " Detail)");
        }

        private Elements memberElements(Element memberDetailElement) {
            // go one level up to find the <ul> with the members
            return memberDetailElement.parent().select("> ul");
        }

        private String signature(Element memberElement) {
            Element signatureElement = memberElement.previousElementSibling();
            String id = signatureElement.attr("id");
            String name = signatureElement.attr("name");
            return id.isEmpty() ? name : id;
        }

        private Element memberSinceTagElement(Element memberElement) {
            return memberElement.selectFirst("li dl > dt > span:contains(Since:)");
        }

        private Element memberDeprecatedBlockElement(Element memberElement) {
            Element element = memberElement.selectFirst("li > div > span.deprecatedLabel");
            if (element == null && javaAPI.getJavadoc().getBaseURL().contains("/7/")) {
                // Java 7 workaround
                element = memberElement.selectFirst("li > div > span.strong:contains(Deprecated)");
            }
            return element;
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