package io.github.h7kz.proofstone.build.pins;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.build.pins.ArtefactPins.Resolved;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Each rule has a failing and a passing case (AGENTS.md section 3).
class ArtefactPinsTest {

    // sha256 of the bytes "abc"
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path dir;

    private Path jarFile() throws IOException {
        Path file = dir.resolve("a.jar");
        Files.writeString(file, "abc");
        return file;
    }

    private Path pins(String... lines) throws IOException {
        Path file = dir.resolve("pins.sha256");
        Files.write(file, List.of(lines));
        return file;
    }

    @Test
    void exactPinPasses() throws IOException {
        assertThat(ArtefactPins.check(List.of(new Resolved("g:a:1.0", jarFile())), pins("# note", ABC + "  g:a:1.0")))
                .isEmpty();
    }

    @Test
    void differentShaFails() throws IOException {
        String wrong = "0".repeat(64);
        assertThat(ArtefactPins.check(List.of(new Resolved("g:a:1.0", jarFile())), pins(wrong + "  g:a:1.0")))
                .singleElement().asString().contains("sha256 " + ABC).contains("pinned " + wrong);
    }

    @Test
    void unpinnedArtefactFailsAndShowsItsSha() throws IOException {
        assertThat(ArtefactPins.check(List.of(new Resolved("g:a:1.0", jarFile())), pins()))
                .singleElement().asString().contains("no pin").contains(ABC);
    }

    @Test
    void leftOverPinFails() throws IOException {
        assertThat(ArtefactPins.check(List.of(), pins(ABC + "  g:a:1.0")))
                .singleElement().asString().contains("not in the resolved graph");
    }

    @Test
    void snapshotAndDynamicVersionsFailEvenWhenPinned() throws IOException {
        for (String version : List.of("1.0-SNAPSHOT", "1.+", "[1.0,2.0)", "latest.release")) {
            String module = "g:a:" + version;
            assertThat(ArtefactPins.check(List.of(new Resolved(module, jarFile())), pins(ABC + "  " + module)))
                    .as(version).singleElement().asString().contains("not a pin");
        }
    }

    @Test
    void aClassifierJarIsItsOwnPinAndItsVersionIsStillChecked() throws IOException {
        String module = "g:a:1.0-20261105.085043-11:linux-x86_64";
        assertThat(ArtefactPins.check(List.of(new Resolved(module, jarFile())), pins(ABC + "  " + module))).isEmpty();
        // the same file without its classifier is another pin, so the plain jar's line does not cover the native one
        assertThat(ArtefactPins.check(List.of(new Resolved(module, jarFile())), pins(ABC + "  g:a:1.0-20261105.085043-11")))
                .anyMatch(v -> v.contains("no pin")).anyMatch(v -> v.contains("not in the resolved graph"));
        String floating = "g:a:1.0-SNAPSHOT:linux-x86_64";
        assertThat(ArtefactPins.check(List.of(new Resolved(floating, jarFile())), pins(ABC + "  " + floating)))
                .singleElement().asString().contains("not a pin");
    }

    @Test
    void theJniPackageOfTheNativeBindingMayStayUnrelocatedButNothingElseMay() throws IOException {
        assertThat(ShadedJarCheck.check(zip("io/github/h7kz/proofstone/Host.class", "tel/schich/libdatachannel/PeerConnection.class")))
                .isEmpty();
        assertThat(ShadedJarCheck.check(zip("io/github/h7kz/proofstone/Host.class", "tel/schich/other/X.class")))
                .singleElement().asString().contains("unrelocated class tel/schich/other/X.class");
    }

    @Test
    void timestampedSnapshotBuildIsAPin() {
        assertThat(ArtefactPins.isFloating("3.0.0.Beta15-20261116.161745-6")).isFalse();
    }

    @Test
    void malformedAndDuplicateLinesFail() throws IOException {
        assertThat(ArtefactPins.check(List.of(), pins("abc g:a:1", ABC + "  g:b:1", ABC + "  g:b:1")))
                .anyMatch(v -> v.contains("not a line of sha256")).anyMatch(v -> v.contains("pinned twice"));
    }

    @Test
    void shadedJarWithOnlyOwnClassesPasses() throws IOException {
        assertThat(ShadedJarCheck.check(zip("io/github/h7kz/proofstone/shaded/netty/A.class",
                "io/github/h7kz/proofstone/driver/bedrock/Host.class", "META-INF/MANIFEST.MF"))).isEmpty();
    }

    @Test
    void aMultiReleaseClassIsJudgedByItsPathAfterTheVersion() throws IOException {
        assertThat(ShadedJarCheck.check(zip("io/github/h7kz/proofstone/Host.class",
                "META-INF/versions/21/io/github/h7kz/proofstone/shaded/A.class"))).isEmpty();
        assertThat(ShadedJarCheck.check(zip("io/github/h7kz/proofstone/Host.class", "META-INF/versions/21/com/google/B.class")))
                .singleElement().asString().contains("unrelocated class META-INF/versions/21/com/google/B.class");
    }

    @Test
    void shadedJarWithAnUnrelocatedClassOrSnapshotEntryFails() throws IOException {
        assertThat(ShadedJarCheck.check(zip("io/github/h7kz/proofstone/Host.class",
                "org/cloudburstmc/protocol/Bedrock.class", "META-INF/maven/x-SNAPSHOT/pom.xml")))
                .hasSize(2).anyMatch(v -> v.contains("unrelocated class org/cloudburstmc"))
                .anyMatch(v -> v.contains("names a snapshot"));
    }

    private Path zip(String... entries) throws IOException {
        Path jar = dir.resolve("shaded.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.closeEntry();
            }
        }
        return jar;
    }
}
