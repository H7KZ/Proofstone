package io.github.h7kz.proofstone.core.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.testcontainers.images.builder.Transferable;

/**
 * A config file copied into a container, owned by the user the server runs as. Docker unpacks a copied tar entry with
 * the owner the entry names, and Testcontainers names root, so a plain copy left {@code bukkit.yml} and
 * {@code spigot.yml} unwritable for the server and Paper logged {@code Could not save ... (Permission denied)} at start
 * (T-M8-02). The itzg images run the server as uid and gid 1000. Still a copy, never a bind mount (ADR-0005).
 */
final class ServerFile implements Transferable {

    /** The user and group the itzg images drop to; the server reads and writes its config as this user. */
    static final int SERVER_ID = 1000;
    /** Owner read and write, everyone else read: a server may rewrite its own config and nothing else may. */
    static final int MODE = 0644;

    private final byte[] content;

    private ServerFile(byte[] content) {
        this.content = content;
    }

    static ServerFile of(String content) {
        return new ServerFile(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public int getFileMode() {
        return MODE;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public void transferTo(TarArchiveOutputStream tar, String destination) {
        TarArchiveEntry entry = new TarArchiveEntry(destination);
        entry.setSize(content.length);
        entry.setMode(MODE);
        entry.setUserId(SERVER_ID);
        entry.setGroupId(SERVER_ID);
        try {
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new PaperHostException("cannot pack " + destination + " for the container: " + e.getMessage(), e);
        }
    }
}
