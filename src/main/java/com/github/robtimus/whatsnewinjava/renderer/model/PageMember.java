/*
 * PageMember.java
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

package com.github.robtimus.whatsnewinjava.renderer.model;

import static java.util.Objects.requireNonNull;
import com.github.robtimus.whatsnewinjava.parser.model.JavaMember;

@SuppressWarnings({ "nls", "javadoc" })
public final class PageMember {

    private final PageClass pageClass;

    private final JavaMember.Type type;
    private final String signature;

    PageMember(PageClass pageClass, JavaMember.Type type, String signature) {
        this.pageClass = requireNonNull(pageClass);

        this.type = requireNonNull(type);
        this.signature = requireNonNull(signature);
    }

    public PageClass getPageClass() {
        return pageClass;
    }

    public JavaMember.Type getType() {
        return type;
    }

    public String getSignature() {
        return signature;
    }

    String getSignatureForCompare() {
        return type == JavaMember.Type.CONSTRUCTOR ? signature.replaceFirst("^.*\\(", "<init>\\(") : signature;
    }

    @Override
    public String toString() {
        return signature;
    }
}
