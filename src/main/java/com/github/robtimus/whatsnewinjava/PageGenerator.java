/*
 * PageGenerator.java
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

package com.github.robtimus.whatsnewinjava;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NavigableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;
import com.github.robtimus.whatsnewinjava.parser.model.JavaVersion;
import com.github.robtimus.whatsnewinjava.renderer.PageRenderer;

@SuppressWarnings({ "nls", "javadoc" })
public final class PageGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageGenerator.class);

    private PageGenerator() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    public static void main(String... args) throws IOException {

        if (args.length < 3) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }

        Path rootFolder = Paths.get(args[0]);
        Path newOutputFile = Paths.get(args[1]);
        Path deprecatedOutputFile = Paths.get(args[2]);
        Path removedOutputFile = Paths.get(args[3]);

        if (!Files.isDirectory(rootFolder)) {
            throw new IllegalArgumentException("Invalid root folder: " + args[0]);
        }

        createParentDirectories(newOutputFile);
        createParentDirectories(deprecatedOutputFile);
        createParentDirectories(removedOutputFile);

        NavigableMap<JavaVersion, JavaAPI> javaAPIs = JavaAPI.allFromJSON(rootFolder);

        PageRenderer pageRenderer = new PageRenderer();
        JavaVersion minimalJavaVersion = Settings.getMinimalJavaVersion();

        LOGGER.info("Rendering deprecated page");
        String deprecatedPage = pageRenderer.renderDeprecatedPage(javaAPIs);
        try (Writer writer = Files.newBufferedWriter(deprecatedOutputFile)) {
            writer.write(deprecatedPage);
        }

        LOGGER.info("Rendering removed page");
        String removedPage = pageRenderer.renderRemovedPage(javaAPIs);
        try (Writer writer = Files.newBufferedWriter(removedOutputFile)) {
            writer.write(removedPage);
        }

        LOGGER.info("Rendering new page");
        String newPage = pageRenderer.renderNewPage(javaAPIs, minimalJavaVersion);
        try (Writer writer = Files.newBufferedWriter(newOutputFile)) {
            writer.write(newPage);
        }
    }

    private static void createParentDirectories(Path outputFile) throws IOException {
        Path outputDir = outputFile.getParent();
        if (outputDir != null) {
            Files.createDirectories(outputDir);
        }
    }
}
