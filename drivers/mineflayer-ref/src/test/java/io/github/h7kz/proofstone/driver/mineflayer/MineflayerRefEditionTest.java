package io.github.h7kz.proofstone.driver.mineflayer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.driver.Edition;
import org.junit.jupiter.api.Test;

/** The reference driver speaks Java Edition (ADR-0018 section 3), without Docker. */
class MineflayerRefEditionTest {

    @Test
    void reportsTheJavaEdition() {
        assertThat(new MineflayerRefDriver().edition()).isEqualTo(Edition.JAVA);
    }
}
