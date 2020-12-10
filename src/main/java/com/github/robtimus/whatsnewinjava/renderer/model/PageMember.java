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
