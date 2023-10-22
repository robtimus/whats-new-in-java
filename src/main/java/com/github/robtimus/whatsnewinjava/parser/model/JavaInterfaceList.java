/*
 * JavaInterfaceList.java
 * Copyright 2019 Rob Spoor
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

package com.github.robtimus.whatsnewinjava.parser.model;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableCollection;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("javadoc")
public final class JavaInterfaceList {

    public static final JavaInterfaceList EMPTY = new JavaInterfaceList(emptySet());

    private final Map<String, String> interfaceNames;

    public JavaInterfaceList(Collection<String> interfaceNames) {
        this.interfaceNames = new TreeMap<>();
        for (String interfaceName : interfaceNames) {
            this.interfaceNames.put(getRawType(interfaceName), interfaceName);
        }
    }

    public Collection<String> interfaceNames() {
        return unmodifiableCollection(interfaceNames.values());
    }

    public boolean hasInterfaceName(String interfaceName) {
        return interfaceNames.containsKey(getRawType(interfaceName));
    }

    public String getGenericInterfaceName(String interfaceName) {
        return interfaceNames.get(getRawType(interfaceName));
    }

    private String getRawType(String interfaceName) {
        int index = interfaceName.indexOf('<');
        return index == -1 ? interfaceName : interfaceName.substring(0, index);
    }

    @Override
    public String toString() {
        return interfaceNames.values().toString();
    }
}
