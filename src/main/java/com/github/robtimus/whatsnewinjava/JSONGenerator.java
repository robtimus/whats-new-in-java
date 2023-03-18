/*
 * JSONGenerator.java
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
import java.util.Set;
import com.github.robtimus.whatsnewinjava.parser.JavadocParser;
import com.github.robtimus.whatsnewinjava.parser.model.JavaAPI;

@SuppressWarnings({ "nls", "javadoc" })
public final class JSONGenerator {

    private JSONGenerator() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    public static void main(String... args) throws IOException {

        if (args.length < 3) {
            throw new IllegalArgumentException("Invalid number of arguments");
        }

        Path rootFolder = Paths.get(args[0]);
        String javadocBaseURL = args[1];
        Path outputDir = Paths.get(args[2]);
        int javaVersion = Integer.parseInt(args[3]);

        if (!Files.isDirectory(rootFolder)) {
            throw new IllegalArgumentException("Invalid root folder: " + args[0]);
        }

        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("java-" + javaVersion + ".json");

        Set<String> packagesToIgnore = Settings.getPackagesToIgnore();

        JavaAPI javaAPI = new JavadocParser().parseJavadoc(rootFolder, packagesToIgnore, javadocBaseURL, javaVersion);

        try (Writer writer = Files.newBufferedWriter(outputFile)) {
            javaAPI.toJSON(writer);
        }
    }
}
