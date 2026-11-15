package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.ResourcePackAnswer;
import io.github.h7kz.proofstone.api.ResourcePackAnswer.Answer;
import io.github.h7kz.proofstone.core.driver.ResourcePackPolicy;
import org.junit.jupiter.api.Test;

/** T-M8-10: which answer a client gives, and that none is ever made up (ADR-0017 section 7). */
class ResourcePackDeclarationTest {

    @ResourcePackAnswer(Answer.ACCEPT)
    static class ClassAccepts {
        void plain() {
        }

        @ResourcePackAnswer(Answer.DECLINE)
        void declines() {
        }
    }

    static class Silent {
        void plain() {
        }
    }

    @Test
    void theMethodsAnswerWinsOverTheClasss() throws NoSuchMethodException {
        assertThat(ResourcePackDeclaration.answer(ClassAccepts.class, ClassAccepts.class.getDeclaredMethod("plain")))
                .isEqualTo(Answer.ACCEPT);
        assertThat(ResourcePackDeclaration.answer(ClassAccepts.class, ClassAccepts.class.getDeclaredMethod("declines")))
                .isEqualTo(Answer.DECLINE);
    }

    @Test
    void noDeclarationIsNoAnswerNeverAnAccept() throws NoSuchMethodException {
        assertThat(ResourcePackDeclaration.answer(Silent.class, Silent.class.getDeclaredMethod("plain"))).isNull();
        assertThat(ResourcePackDeclaration.forTest(null, null)).isEqualTo(ResourcePackPolicy.NONE);
        assertThat(ResourcePackDeclaration.forHarness(null)).as("no pack served: the harness's clients declare nothing")
                .isEqualTo(ResourcePackPolicy.NONE);
    }
}
