package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.ResourcePackAnswer;
import io.github.h7kz.proofstone.api.ServerResourcePack;
import io.github.h7kz.proofstone.core.driver.ResourcePackPolicy;
import io.github.h7kz.proofstone.core.host.ResourcePackSource;
import java.lang.reflect.Method;

/** What a test class declares about resource packs, read once per need (ADR-0017 section 7, T-M8-10). */
final class ResourcePackDeclaration {

    private ResourcePackDeclaration() {
    }

    /** The test's answer: the method's {@link ResourcePackAnswer}, else the class's, else {@code null} (none declared). */
    static ResourcePackAnswer.Answer answer(Class<?> testClass, Method test) {
        ResourcePackAnswer onMethod = test.getAnnotation(ResourcePackAnswer.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        ResourcePackAnswer onClass = testClass.getAnnotation(ResourcePackAnswer.class);
        return onClass == null ? null : onClass.value();
    }

    /**
     * The policy of a test's own clients: its declared answer, downloading only from the cell's source. With no answer
     * declared a push ends the join loudly, whether or not the cell serves a pack.
     */
    static ResourcePackPolicy forTest(ResourcePackAnswer.Answer answer, ResourcePackSource source) {
        return new ResourcePackPolicy(answer, source == null ? null : source.base());
    }

    /**
     * The policy of the harness's own clients (the per-cell conformance check): they accept the cell's declared pack,
     * which only the harness serves, so a required pack does not keep them out. With no pack served they declare nothing.
     */
    static ResourcePackPolicy forHarness(ResourcePackSource source) {
        return source == null ? ResourcePackPolicy.NONE
                : new ResourcePackPolicy(ResourcePackAnswer.Answer.ACCEPT, source.base());
    }

    /** The image's variables for the declared pack, served by {@code source}. */
    static java.util.Map<String, String> env(ServerResourcePack declared, ResourcePackSource source) {
        return source.env(declared.required(), declared.prompt(), declared.sha1());
    }
}
