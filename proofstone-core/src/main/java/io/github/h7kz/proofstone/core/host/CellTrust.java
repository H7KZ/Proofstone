package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.host.standin.StandIn;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The per-run certificate of the discovery stand-in and the trust store that trusts it (ADR-0018 section 4). The
 * certificate comes from the {@code keytool} of the JDK that runs the harness (a JRE-only harness fails loudly), made in a
 * temporary directory that is deleted at once; the key and certificate are held in memory and are never committed. The
 * trust store is the pinned image's {@code cacerts} plus the stand-in certificate, built with the {@code KeyStore} API.
 */
final class CellTrust {

    private static final String ALIAS = "proofstone-stand-in";

    private final byte[] keystore;
    private final byte[] certificate;

    private CellTrust(byte[] keystore, byte[] certificate) {
        this.keystore = keystore;
        this.certificate = certificate;
    }

    /** A new key pair and self-signed certificate whose SAN is the two stand-in names. */
    static CellTrust generate() {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("proofstone-stand-in");
            Path store = dir.resolve("stand-in.p12");
            Path crt = dir.resolve("stand-in.crt");
            String san = "san=dns:" + String.join(",dns:", StandIn.NAMES.stream().sorted().toList());
            // Valid from yesterday for three days: the container clock and this one need not agree to the minute.
            keytool(dir, "-genkeypair", "-alias", ALIAS, "-keyalg", "EC", "-groupname", "secp256r1", "-dname",
                    "CN=proofstone-stand-in", "-startdate", "-1d", "-validity", "3", "-ext", san, "-keystore",
                    store.toString(), "-storetype", "PKCS12", "-storepass", StandIn.STORE_PASSWORD, "-keypass",
                    StandIn.STORE_PASSWORD);
            keytool(dir, "-exportcert", "-alias", ALIAS, "-keystore", store.toString(), "-storepass",
                    StandIn.STORE_PASSWORD, "-rfc", "-file", crt.toString());
            return new CellTrust(Files.readAllBytes(store), Files.readAllBytes(crt));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot generate the stand-in certificate", e);
        } finally {
            delete(dir);
        }
    }

    /** The PKCS12 keystore the stand-in serves TLS with (private key inside); copied into its container only. */
    byte[] stubKeystore() {
        return keystore.clone();
    }

    /** The stand-in certificate, PEM. */
    byte[] certificate() {
        return certificate.clone();
    }

    /** {@code cacerts} (any keystore type the JDK reads, password {@code changeit}) plus the certificate, as PKCS12. */
    byte[] trustStore(byte[] cacerts) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            char[] password = StandIn.STORE_PASSWORD.toCharArray();
            Path file = Files.createTempFile("proofstone-cacerts", ".bin");
            try {
                Files.write(file, cacerts);
                KeyStore source = KeyStore.getInstance(file.toFile(), password);
                store.load(null, null);
                for (String alias : java.util.Collections.list(source.aliases())) {
                    store.setCertificateEntry(alias, source.getCertificate(alias));
                }
            } finally {
                Files.deleteIfExists(file);
            }
            try (InputStream in = new ByteArrayInputStream(certificate)) {
                Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
                store.setCertificateEntry(ALIAS, cert);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            store.store(out, password);
            return out.toByteArray();
        } catch (GeneralSecurityException | IOException e) {
            throw new PaperHostException("cannot build the cell trust store: " + e.getMessage(), e);
        }
    }

    /** The two JVM options that make a JVM trust {@code path}; set as {@code JAVA_TOOL_OPTIONS} on the server and driver host. */
    static String javaToolOptions(String path) {
        return "-Djavax.net.ssl.trustStore=" + path + " -Djavax.net.ssl.trustStorePassword=" + StandIn.STORE_PASSWORD;
    }

    private static void keytool(Path dir, String... args) throws IOException {
        Path home = Path.of(System.getProperty("java.home"), "bin");
        Path tool = Files.isRegularFile(home.resolve("keytool.exe")) ? home.resolve("keytool.exe") : home.resolve("keytool");
        if (!Files.isRegularFile(tool)) {
            throw new PaperHostException("no keytool in " + home + ": the harness needs a JDK to make the stand-in "
                    + "certificate (ADR-0018 section 4)");
        }
        List<String> command = new java.util.ArrayList<>(List.of(tool.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new PaperHostException("keytool " + args[0] + " failed: " + new String(output));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaperHostException("keytool " + args[0] + " was interrupted", e);
        }
    }

    private static void delete(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete " + dir, e);
        }
    }
}
