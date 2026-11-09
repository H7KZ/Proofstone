package io.github.h7kz.proofstone.agent.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP client for talking to an {@link AgentServer} on the loopback interface. It writes the request
 * by hand because HttpURLConnection refuses PATCH, and the agent serves PATCH. Each call is one connection
 * with Connection: close, so reading to end of stream gets the whole reply.
 */
final class AgentHttp {

    private final int port;

    AgentHttp(int port) {
        this.port = port;
    }

    /** {@code authorization} and {@code body} may be null, meaning the header or body is not sent. */
    Response send(String method, String path, String authorization, String body) throws IOException {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: 127.0.0.1\r\n")
                .append("Connection: close\r\n");
        if (authorization != null) {
            head.append("Authorization: ").append(authorization).append("\r\n");
        }
        if (body != null) {
            head.append("Content-Type: application/json; charset=utf-8\r\n")
                    .append("Content-Length: ").append(payload.length).append("\r\n");
        }
        head.append("\r\n");

        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
            String reply = read(socket.getInputStream());
            int headerEnd = reply.indexOf("\r\n\r\n");
            // Status line is "HTTP/1.1 <code> <reason>".
            int status = Integer.parseInt(reply.substring(9, 12));
            return new Response(status, reply.substring(headerEnd + 4));
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
