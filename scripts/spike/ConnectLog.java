// T-M8-07 measurement tool, not production code. A forward proxy that records the host of every request and tunnels it,
// run inside the pinned itzg image on a network the server container cannot leave
// any other way. It writes one line per request to the file named by args[1]: "CONNECT host:port" or "HTTP host".
// Usage: java ConnectLog <port> <log file> (compiled by install-hosts.sh)
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ConnectLog {
    private static Path log;

    public static void main(String[] args) throws IOException {
        log = Path.of(args[1]);
        try (ServerSocket server = new ServerSocket(Integer.parseInt(args[0]))) {
            System.out.println("proxy up on " + args[0]);
            while (true) {
                Socket client = server.accept();
                Thread.ofVirtual().start(() -> serve(client));
            }
        }
    }

    private static void serve(Socket client) {
        try (client) {
            InputStream in = client.getInputStream();
            String head = readHead(in);
            String[] first = head.split("\r\n")[0].split(" ");
            String target;
            byte[] replay = null;
            if (first[0].equals("CONNECT")) {
                target = first[1];
                record("CONNECT " + target);
            } else {
                java.net.URI uri = java.net.URI.create(first[1]);
                target = uri.getHost() + ":" + (uri.getPort() > 0 ? uri.getPort() : 80);
                record("HTTP " + uri.getHost());
                String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                replay = (first[0] + " " + path + " " + first[2] + head.substring(head.indexOf("\r\n")) + "\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1);
            }
            int colon = target.lastIndexOf(':');
            try (Socket upstream = new Socket()) {
                upstream.connect(new InetSocketAddress(target.substring(0, colon), Integer.parseInt(target.substring(colon + 1))), 15000);
                OutputStream out = client.getOutputStream();
                if (replay == null) {
                    out.write("HTTP/1.1 200 Connection established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } else {
                    upstream.getOutputStream().write(replay);
                }
                Thread pump = Thread.ofVirtual().start(() -> copy(in, upstream));
                copy(upstream.getInputStream(), client);
                pump.join(2000);
            }
        } catch (Exception e) {
            record("ERROR " + e);
        }
    }

    private static String readHead(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.append((char) b);
        }
        return head.toString();
    }

    private static void copy(InputStream from, Object to) {
        try {
            OutputStream out = to instanceof Socket s ? s.getOutputStream() : null;
            byte[] buffer = new byte[16384];
            for (int n; (n = from.read(buffer)) >= 0;) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // closed by the peer
        }
    }

    private static synchronized void record(String line) {
        try {
            Files.writeString(log, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
