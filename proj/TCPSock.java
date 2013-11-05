import com.sun.tools.javac.util.Pair;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }

    enum SockType {
        // Type of socket
        UNDEFINED, // Gets assigned in the constructor
        SERVER_SOCKET,    // If listen is called, we have a server socket (src_ip, src_port, *, *)
        SERVER_CONNECTION, // Can be instantiated only by a SERVER_SOCKET;
                            // handles a connection with a client (src_ip, src_port, dest_ip, dest_port)
        CLIENT_CONNECTION     // If connect is called, we have a client socket
    }

    private State state;
    private Node node;
    private TCPManager tcpManager;
    private int localPort;
    private SockType sockType;

    private int remoteAddr;
    private int remotePort;

    private int backlog;

    private ISocketSpace pendingConnections;
    private ISocketSpace workingConnections;

    public TCPSock(Node node, TCPManager tcpManager) {
        this.node = node;
        this.tcpManager = tcpManager;

        this.sockType = SockType.UNDEFINED;
        this.state = State.CLOSED;

        this.remotePort = this.remoteAddr = -1;
    }

    private TCPSock(Node node, TCPManager tcpManager, int localPort, SockType sockType) throws Exception {

        if (sockType != SockType.SERVER_CONNECTION)
            throw new Exception("This constructor can only be used for server connection sockets!");

        this.node = node;
        this.tcpManager = tcpManager;
        this.sockType = sockType;
        this.localPort = localPort;
        this.state = State.ESTABLISHED;

        this.remotePort = this.remoteAddr = -1;
    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        if (tcpManager.registerSock(localPort, this) == -1)
            return -1;

        this.localPort = localPort;
        return 0;
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        this.sockType = SockType.SERVER_SOCKET;
        this.backlog = backlog;
        this.state = State.LISTEN;

        this.pendingConnections = new SocketSpace<RemoteHost>(backlog);
        this.workingConnections = new SocketSpace<RemoteHost>();

        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        Pair<RemoteHost, TCPSock> currentConnection = pendingConnections.pop();
        if (currentConnection == null)
            return null;

        workingConnections.register(currentConnection.fst, currentConnection.snd);
        return currentConnection.snd;
    }

    public boolean isConnectionPending() {
        return (state == State.SYN_SENT);
    }

    public boolean isClosed() {
        return (state == State.CLOSED);
    }

    public boolean isConnected() {
        return (state == State.ESTABLISHED);
    }

    public boolean isClosurePending() {
        return (state == State.SHUTDOWN);
    }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        if (this.sockType == SockType.UNDEFINED)
            this.sockType = SockType.CLIENT_CONNECTION;

        if (this.remoteAddr != -1 || this.remotePort != -1) {
            node.logError("Socket already bound to address" + this.remoteAddr + " port: " + this.remotePort);
            return -1;
        }

        this.remoteAddr = destAddr;
        this.remotePort = destPort;

        return 0;
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        return -1;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        return -1;
    }

    /*
     * End of socket API
     */

    public int getLocalPort() {
        return localPort;
    }
}
