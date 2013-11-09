import java.util.Map;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
public class TCPManager {
    private Node node;
    private int addr;
    private Manager manager;

    private static final byte dummy[] = new byte[0];

    private ISocketSpace socketSpace;

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
        this.socketSpace = new SocketSpace<Integer>();
    }

    /**
     * Start this TCP manager
     */
    public void start() {

    }

    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
        return new TCPSock(node, this);
    }

    public int registerSock(int localPort, TCPSock socket) {
        return (socketSpace.register(localPort, socket));
    }

    public int deregisterSock(int localPort) {
        return (socketSpace.deregister(localPort));
    }

    public void incomingTransportPacket(int remoteAddr, int remotePort, int localAddr, int localPort, Transport transportMessage) {
        if (socketSpace.portBusy(localPort)) {
            // We have already created a connection socket for this port
            TCPSock socket = socketSpace.get(localPort);
            socket.receive(remoteAddr, remotePort, transportMessage);
        } else {
            // Well if I don't find the port here, then nobody's listening for your packet mwhahahahaaaa!
            // drop;
        }
    }

    /*
     * End Socket API
     */
}
