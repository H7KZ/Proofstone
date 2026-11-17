// T-M9-01 measurement tool, not production code. Can Testcontainers (2.0.5, the version Proofstone pins) expose a UDP port, and
// can a host process then reach it? The container runs a 10-line Python UDP echo on 19132 (RakNet's port) in the pinned itzg
// image. Two attempts, both reported: (1) the public API a Host would use, withExposedPorts(19132) and getMappedPort(19132),
// which is TCP only; (2) a create-container modifier that publishes 19132/udp on 127.0.0.1 and a mapped port read from
// the container inspect. Usage: java -cp <classpath> TcUdpProbe.java <image>
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class TcUdpProbe {
    static final String PY = "import socket\n"
            + "s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)\n"
            + "s.bind(('0.0.0.0',19132))\n"
            + "print('udp echo up',flush=True)\n"
            + "while True:\n"
            + "    d,a=s.recvfrom(2048)\n"
            + "    s.sendto(d,a)\n";

    public static void main(String[] args) throws Exception {
        DockerImageName image = DockerImageName.parse(args[0]);
        // (1) the API a Host has today: TCP mapping only.
        try (GenericContainer<?> c = new GenericContainer<>(image)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("python3", "-c", PY))
                .withExposedPorts(19132)
                .waitingFor(Wait.forLogMessage(".*udp echo up.*", 1))) {
            c.start();
            Integer mapped = c.getMappedPort(19132);
            System.out.println("(1) withExposedPorts(19132) -> getMappedPort(19132) = " + mapped + " (a TCP binding)");
            System.out.println("(1) echo over UDP to 127.0.0.1:" + mapped + " -> " + echo(mapped));
        } catch (Exception e) {
            System.out.println("(1) failed: " + e);
        }
        // (2) UDP published through the create-container modifier.
        try (GenericContainer<?> c = new GenericContainer<>(image)
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withEntrypoint("python3", "-c", PY);
                    cmd.withExposedPorts(ExposedPort.udp(19132));
                    cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.udp(19132)));
                })
                .waitingFor(Wait.forLogMessage(".*udp echo up.*", 1))) {
            c.start();
            Ports.Binding[] b = c.getContainerInfo().getNetworkSettings().getPorts().getBindings().get(ExposedPort.udp(19132));
            int hostPort = Integer.parseInt(b[0].getHostPortSpec());
            System.out.println("(2) published 19132/udp -> " + b[0].getHostIp() + ":" + hostPort);
            try {
                System.out.println("(2) getMappedPort(19132) = " + c.getMappedPort(19132));
            } catch (Exception e) {
                System.out.println("(2) getMappedPort(19132) throws: " + e.getMessage());
            }
            System.out.println("(2) echo over UDP to 127.0.0.1:" + hostPort + " -> " + echo(hostPort));
        }
    }

    // One datagram out, one back, bounded wait (the await shape).
    static String echo(int port) {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(5000);
            byte[] out = "proofstone-udp-probe".getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(out, out.length, InetAddress.getByName("127.0.0.1"), port));
            DatagramPacket in = new DatagramPacket(new byte[256], 256);
            s.receive(in);
            return "echoed " + new String(in.getData(), 0, in.getLength(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "NO ECHO (" + e + ")";
        }
    }
}
