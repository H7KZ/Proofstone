package io.github.h7kz.proofstone.driver.mcpl;

import io.github.h7kz.proofstone.api.ResourcePackAnswer.Answer;
import io.github.h7kz.proofstone.api.ResourcePackPush;
import io.github.h7kz.proofstone.core.driver.ResourcePackPolicy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPopPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;

/**
 * Records every resource-pack push and pop and answers each push as the test declared (client-driver spec section 2b,
 * ADR-0017 section 7). It downloads only from the harness source and only to check the sha1; nothing is unpacked.
 */
final class ResourcePacks {

    /** Fetches a pack's bytes; the driver's is a plain HTTP GET that follows no redirect. */
    interface Fetcher {
        byte[] fetch(String url) throws IOException, InterruptedException;
    }

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(20);

    private final ResourcePackPolicy policy;
    private final Fetcher fetcher;
    /** Where downloads run: never the network thread, which must keep reading packets (keep-alives) meanwhile. */
    private final Executor downloads;
    private final Consumer<Packet> send;
    /** Ends the session with a reason; the session then fails every wait with it. */
    private final Consumer<String> abort;
    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    ResourcePacks(ResourcePackPolicy policy, Fetcher fetcher, Executor downloads, Consumer<Packet> send,
            Consumer<String> abort) {
        this.policy = policy;
        this.fetcher = fetcher;
        this.downloads = downloads;
        this.send = send;
        this.abort = abort;
    }

    /** One push as seen, with what was answered; mutable only under its own lock. */
    private static final class Entry {
        final String id;
        final String url;
        final String hash;
        final boolean required;
        final String prompt;
        final List<String> statuses = new ArrayList<>();
        String problem;
        boolean popped;

        Entry(ClientboundResourcePackPushPacket push) {
            id = push.getId().toString();
            url = push.getUrl();
            hash = push.getHash() == null ? "" : push.getHash();
            required = push.isRequired();
            prompt = push.getPrompt() == null ? null : McplSession.flatten(push.getPrompt());
        }

        synchronized ResourcePackPush snapshot(Answer answer) {
            return new ResourcePackPush(id, url, hash, required, prompt, answer, statuses, problem, popped);
        }
    }

    /** Records and answers a push; returns its transcript summary. */
    String onPush(ClientboundResourcePackPushPacket push) {
        Entry entry = new Entry(push);
        entries.add(entry);
        String summary = "id=" + entry.id + " url=" + entry.url + " hash=" + entry.hash + " required=" + entry.required;
        Answer answer = policy.answer();
        if (answer == null) {
            // Never a silent accept and never a stall into a join timeout (ADR-0017 section 7).
            problem(entry, "no resource-pack answer declared");
            abort.accept("resource pack pushed (" + entry.url + ") but no resource-pack answer declared: declare "
                    + "@ResourcePackAnswer on the test (ADR-0017 section 7)");
            return summary + " (no answer declared: disconnecting)";
        }
        switch (answer) {
            case DECLINE -> status(push, entry, ResourcePackStatus.DECLINED);
            case FAIL_DOWNLOAD -> {
                status(push, entry, ResourcePackStatus.ACCEPTED);
                status(push, entry, ResourcePackStatus.FAILED_DOWNLOAD);
            }
            case ACCEPT -> {
                if (!policy.fromSource(entry.url)) {
                    problem(entry, "not the harness source " + policy.source());
                    abort.accept("ACCEPT on a resource pack that is not from the harness source: " + entry.url
                            + " (source " + policy.source() + "); the driver never downloads from anywhere else "
                            + "(client-driver spec section 2b)");
                    return summary + " (not the harness source: disconnecting)";
                }
                status(push, entry, ResourcePackStatus.ACCEPTED);
                downloads.execute(() -> download(push, entry));
            }
        }
        return summary + " answer=" + answer;
    }

    private void download(ClientboundResourcePackPushPacket push, Entry entry) {
        byte[] bytes;
        try {
            bytes = fetcher.fetch(entry.url);
        } catch (IOException | InterruptedException | RuntimeException e) {
            problem(entry, "download failed: " + e);
            status(push, entry, ResourcePackStatus.FAILED_DOWNLOAD);
            return;
        }
        String got = sha1(bytes);
        if (entry.hash.isEmpty()) {
            // A vanilla client loads a pack with no hash unchecked; the record says it was not checked.
            problem(entry, "no hash pushed, so the download (sha1 " + got + ") was not checked");
        } else if (!entry.hash.equalsIgnoreCase(got)) {
            problem(entry, "hash mismatch: expected " + entry.hash + ", got " + got);
            status(push, entry, ResourcePackStatus.FAILED_DOWNLOAD);
            return;
        }
        status(push, entry, ResourcePackStatus.DOWNLOADED);
        status(push, entry, ResourcePackStatus.SUCCESSFULLY_LOADED);
    }

    /** Records a pop of one id, or of every pack when the id is absent; returns its transcript summary. */
    String onPop(ClientboundResourcePackPopPacket pop) {
        String id = pop.getId() == null ? null : pop.getId().toString();
        int matched = 0;
        synchronized (entries) {
            for (Entry entry : entries) {
                if (id == null || entry.id.equals(id)) {
                    synchronized (entry) {
                        entry.popped = true;
                    }
                    matched++;
                }
            }
        }
        return (id == null ? "all" : "id=" + id) + (matched == 0 ? " (no such pack)" : "");
    }

    List<ResourcePackPush> snapshot() {
        synchronized (entries) {
            return entries.stream().map(entry -> entry.snapshot(policy.answer())).toList();
        }
    }

    private void status(ClientboundResourcePackPushPacket push, Entry entry, ResourcePackStatus status) {
        // Recorded before the send, so a test that sees the server react never reads a shorter list.
        synchronized (entry) {
            entry.statuses.add(status.name());
        }
        send.accept(new ServerboundResourcePackPacket(push.getId(), status));
    }

    private static void problem(Entry entry, String problem) {
        synchronized (entry) {
            entry.problem = problem;
        }
    }

    static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("the JDK has no SHA-1", e);
        }
    }

    /** The driver's fetcher: one GET, no redirect (so the source rule cannot be bypassed), 200 or an error. */
    static Fetcher http() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(DOWNLOAD_TIMEOUT).build();
        return url -> {
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(DOWNLOAD_TIMEOUT).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
            return response.body();
        };
    }
}
