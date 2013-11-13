import com.sun.tools.javac.util.Pair;

import java.lang.reflect.Method;
import java.util.Random;

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

    public static int RCV_BUFFER_SIZE = 65536;
    public static int CLOSE_TIMEOUT = 1000; // Check every second if all data has been read from buffer and can close socket
    public static int RETRANSMIT_DELAY = 500;

    private State state;
    private Node node;
    private TCPManager tcpManager;
    private int localPort;
    private SockType sockType;

    private int remoteAddr;
    private int remotePort;

    private int backlog;

    private int seqNo;

    private Random randomGenerator;

    private TCPSockBuffer receiveBuffer;
    private TCPSockBuffer sendBuffer;

    private ISocketSpace pendingConnections;
    private ISocketSpace workingConnections;

    private Transport lastSentPacket;
    int lastAckReceived;

    private long lastAction;

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        if (tcpManager.registerSock(localPort, this) == -1) {
            node.logError("Error in binding to local port " + localPort);
            return -1;
        }

        this.localPort = localPort;
        return 0;
    }

    public TCPSock(Node node, TCPManager tcpManager) {
        this.node = node;
        this.tcpManager = tcpManager;

        this.sockType = SockType.UNDEFINED;
        this.state = State.CLOSED;

        this.remotePort = this.remoteAddr = -1;
        this.lastAckReceived = -1;
    }

    private TCPSock(Node node, TCPManager tcpManager, int localPort, SockType sockType) throws Exception {

        if (sockType != SockType.SERVER_CONNECTION)
            throw new Exception("This constructor can only be used for server connection sockets!");

        this.node = node;
        this.tcpManager = tcpManager;
        this.sockType = sockType;
        this.localPort = localPort;
        this.state = State.SYN_SENT;
        this.seqNo = 0;

        this.remotePort = this.remoteAddr = -1;
    }

    public void receive(int remoteAddr, int remotePort, Transport transportMessage) {
        if (this.sockType == SockType.UNDEFINED) {
            // Oups shouldn't be here!
            node.logError("Socket of undefined type is receiving data wtf man!");
            return;
        }

        RemoteHost remoteHost = new RemoteHost(remoteAddr, remotePort);
        if (this.sockType == SockType.SERVER_SOCKET) {
            if (!pendingConnections.portBusy(remoteHost) && !workingConnections.portBusy(remoteHost)
                    && transportMessage.getType() != Transport.SYN) {
                node.logError("No socket available for connection with " + remoteHost.toString());
                return;
            }
        }

        switch (transportMessage.getType()) {
            case Transport.SYN:
                this.receiveSyn(remoteHost, transportMessage);
                break;
            case Transport.ACK:
                this.receiveAck(remoteHost, transportMessage);
                break;
            case Transport.FIN:
                this.receiveFin(remoteHost, transportMessage);
                break;
            case Transport.DATA:
                this.receiveData(remoteHost, transportMessage);
        }
    }

    private void sendSynReply(Transport synRequest) throws Exception {
        if (this.sockType != SockType.SERVER_CONNECTION) {
            throw new Exception("Expected to have SERVER_CONNECTION socket, got" + this.sockType);
        }

        node.logError("Sent SYN reply to " + this.remoteAddr + ":" + this.remotePort);
        this.seqNo = synRequest.getSeqNum() + 1;
        Transport synAck = new Transport(this.localPort, this.remotePort, Transport.ACK, 0, this.seqNo, new byte[0]);
        this.sendTransportPacket(synAck);
    }

    private void receiveSyn(RemoteHost remoteHost, Transport transportMessage) {
        if (this.sockType == SockType.SERVER_SOCKET) {
            node.logOutput("S");
            node.logError("From " + remoteHost.toString());
            node.logError("Demultiplexing: " + pendingConnections.portBusy(remoteHost) + " " + workingConnections.portBusy(remoteHost));

            TCPSock connectionSocket = null;
            pendingConnections.cleanup();
            workingConnections.cleanup();

            if (!pendingConnections.portBusy(remoteHost) && !workingConnections.portBusy(remoteHost)) {
                int noConnections = pendingConnections.size() + workingConnections.size();
                if (noConnections >= backlog) {
                    node.logError("Too many connections! Already have " + noConnections + " can not accept from " + remoteHost.toString());
                    return;
                }
                try {
                    connectionSocket = new TCPSock(node, tcpManager, localPort, SockType.SERVER_CONNECTION);
                    connectionSocket.connect(remoteHost.getAddress(), remoteHost.getPort());
                } catch (Exception e) {
                    node.logError("Exception in creating connection socket!" + e);
                    return;
                }

                pendingConnections.register(remoteHost, connectionSocket);
            } else {
                if (pendingConnections.portBusy(remoteHost)) { // Received second SYN because my ACK got lost
                    connectionSocket = pendingConnections.get(remoteHost);
                } else if (workingConnections.portBusy(remoteHost)) { // Probably did not deregister old socket
                    connectionSocket = workingConnections.get(remoteHost);
                    if (connectionSocket.state == State.CLOSED)
                        workingConnections.deregister(remoteHost);
                }
            }

            try {
                connectionSocket.updateLastAction();
                connectionSocket.sendSynReply(transportMessage);
            } catch (Exception e) {
                node.logError("Exception in replying to SYN " + e);
                return;
            }

            node.logError("Incoming connection from " + remoteHost.toString());
        } else {
            node.logError("Received SYN in something else than a server socket!");
        }
    }

    private void receiveAck(RemoteHost remoteHost, Transport transportMessage) {
        if (this.sockType == SockType.CLIENT_CONNECTION) {
            if (this.state == State.CLOSED) { // Sent FIN, received ACK for FIN
                return;
            }
            //TODO: This shit is only for stop'n'wait
            if (transportMessage.getSeqNum() > this.seqNo)
                node.logOutput(":");
            else
                node.logOutput("?");

            node.logError("Received reply from server " + remoteHost.toString() + " like a boss! " + transportMessage.getSeqNum());
            this.state = State.ESTABLISHED;
            lastAckReceived = transportMessage.getSeqNum();
        }
    }

    private void receiveFin(RemoteHost remoteHost, Transport transportMessage) {
        if (this.sockType == SockType.SERVER_SOCKET) {
            TCPSock connectionSocket = null;

            if (workingConnections.portBusy(remoteHost))
                connectionSocket = workingConnections.get(remoteHost);
            else
                connectionSocket = pendingConnections.get(remoteHost);

            connectionSocket.receiveFin(remoteHost, transportMessage);
            //workingConnections.deregister(remoteHost);
            return;
        }

        node.logOutput("F");

        if (this.sockType == SockType.CLIENT_CONNECTION) {
            this.state = State.CLOSED;
            tcpManager.deregisterSock(this.localPort);
        } else if (this.sockType == SockType.SERVER_CONNECTION) {
            node.logError("Received FIN on server connection socket " + this.seqNo + " " + transportMessage.getSeqNum());
            if (transportMessage.getSeqNum() != this.seqNo) {
                node.logError("Dropped!");
                return; // TODO: DROP? is it ok?
            }
            this.state = State.CLOSED;
            this.close();
        } else {
            //shouldn't be here!
            node.logError("Release requested on an unknown socket type! Noop.");
            return;
        }

    }

    private void receiveData(RemoteHost remoteHost, Transport transportMessage) {
        if (this.sockType == SockType.SERVER_SOCKET) {
            TCPSock connectionSocket = null;

            if (workingConnections.portBusy(remoteHost))
                connectionSocket = workingConnections.get(remoteHost);
            else
                connectionSocket = pendingConnections.get(remoteHost);

            if (connectionSocket == null) {
                node.logError("receiveData: connectionSocket is null! " + workingConnections.size() + " " + pendingConnections.size() + " " +
                    remoteHost.toString());
                return;
            }
            connectionSocket.receiveData(remoteHost, transportMessage);
            return;
        }

        updateLastAction();
        node.logError("Received " + transportMessage.getPayload().length + " bytes of data from " + remoteHost);
        node.logError("Local seqNo is " + this.seqNo + " Remote seqNo is " + transportMessage.getSeqNum());

        if (this.seqNo != transportMessage.getSeqNum()) {
            if (this.seqNo > transportMessage.getSeqNum()) { // Resend ACK
                node.logOutput("!");
                Transport ackMessage = new Transport(this.localPort, this.remotePort, Transport.ACK, 0, transportMessage.getSeqNum() + transportMessage.getPayload().length, new byte[0]);
                sendTransportPacket(ackMessage);
                return;
            } else { // Received packet from the future; TODO: DROP?!?
                node.logError("Drop! " + this.seqNo + " " + transportMessage.getSeqNum());
                return;
            }
        }

        node.logOutput(".");
        if (receiveBuffer.append(transportMessage.getPayload()) == -1) {
            //TODO: Receive buffer full; what do i do?
            node.logError("FUCK FUCK FUCK Socket receive buffer is full for port " + this.localPort);
            return;
        }

        //TODO: Send an ACK here
        this.seqNo = this.seqNo + transportMessage.getPayload().length;
        Transport ackMessage = new Transport(this.localPort, this.remotePort, Transport.ACK, 0, this.seqNo, new byte[0]);
        sendTransportPacket(ackMessage);
        // FUTU-TI MORTII MA-TII DE TEMA BAGA-MI-AS PULA IN GURA TA PIZDA MA-TII SA-MI BAGI MANA IN CUR SI SA-MI FACI O LABA LA CACAT
    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

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

        //TODO: Create task that fires periodically for removing closed sockets

        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        Pair<RemoteHost, TCPSock> currentConnection = pendingConnections.pop();
        if (currentConnection == null) {
            return null;
        }

        int result = workingConnections.register(currentConnection.fst, currentConnection.snd);
        node.logError("accept(): workingConnections.register returned " + result);
        return currentConnection.snd;
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
        this.receiveBuffer = new TCPSockBuffer(RCV_BUFFER_SIZE);

        if (this.sockType == SockType.CLIENT_CONNECTION) {
            this.randomGenerator = new Random(System.nanoTime());
            this.seqNo = randomGenerator.nextInt(1000000);

            // Send SYN packet to server
            Transport synMessage = new Transport(this.localPort, this.remotePort, Transport.SYN, 0, this.seqNo, new byte[0]);
            this.sendTransportPacket(synMessage);
            this.state = State.SYN_SENT;

        } else if (this.sockType == SockType.SERVER_CONNECTION) {
            this.state = State.ESTABLISHED;
        }

        return 0;
    }

    // Method for sending non-DATA packets
    // All the DATA packets should be sent using write()
    // TODO: Check for ^
    // Increase seqNo in method calling this, not here
    private void sendTransportPacket(Transport data) {

        switch (data.getType()) {
            case Transport.FIN:
                node.logOutput("F");
                break;
            case Transport.SYN:
                node.logOutput("S");
                break;
        }

        //TODO: Do I have to do this for server too?
        if (this.sockType == SockType.CLIENT_CONNECTION) {
            if (data.getType() != Transport.FIN)
                createTimer(data);
        }
        //TODO: Check if I'm in the right type of socket
        node.logError("Sent packet to " + this.remoteAddr + ":" + this.remotePort + " with seqNo=" + data.getSeqNum());
        byte[] packetPayload = data.pack();
        this.node.sendSegment(node.getAddr(), this.remoteAddr, Protocol.TRANSPORT_PKT, packetPayload);
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
        //TODO: Figure out cases when either return -1 or something lower than len
        //TODO: limit payload size to Transport.MAX_PAYLOAD_SIZE
        node.logOutput(".");
        byte[] payload = new byte[Math.min(len, Transport.MAX_PAYLOAD_SIZE)];
        int sentBytes = 0;
        for (int i = pos; i < Math.min(pos + len, buf.length) && sentBytes < Transport.MAX_PAYLOAD_SIZE; i++) {
            sentBytes++;
            payload[i - pos] = buf[i];
        }

        if (this.lastAckReceived >= this.seqNo + 1) { // Nothing dropped until now, not waiting for an ACK for an old message
            // seqNo represents the number of bytes sent until now.
            // seqNo + 1 is the first byte from the packet that's being sent now
            node.logError("Sending data to server with seqNo = " + (this.seqNo + 1));
            lastSentPacket = new Transport(this.localPort, this.remotePort, Transport.DATA, 0, this.seqNo + 1, payload);

            byte[] packet = lastSentPacket.pack();

            //TODO: Is this supposed to be here?
            this.seqNo += sentBytes;

            node.sendSegment(node.getAddr(), this.remoteAddr, Protocol.TRANSPORT_PKT, packet);
            createTimer(lastSentPacket);
            return sentBytes;
        } else {
            node.logError("Packet not sent because I haven't received ACK from previous one! " + this.lastAckReceived + " " + this.seqNo);
            return 0;
        }
    }

    private void createTimer(Transport lastSentPacket) {
        String[] paramTypes = new String[1];
        paramTypes[0] = "Transport";

        Object[] params = new Object[1];
        params[0] = lastSentPacket;

        node.logError("Created timer for " + lastSentPacket.getSeqNum() + " at time " + System.currentTimeMillis());
        addTimer(RETRANSMIT_DELAY, this, "checkResend", paramTypes, params);
    }

    public void checkResend(Transport message) {
        node.logError("checkResend for " + message.getSeqNum() + " " + message.getType() + " " + System.currentTimeMillis());
        node.logError("checkResend: " + message.getSeqNum() + " " + this.lastAckReceived);
        node.logError("checkResend: Current seqNo at client is: " + this.seqNo);
        node.logError("\n");
        if (message.getType() == Transport.DATA) {
            if (this.lastAckReceived < message.getSeqNum() + message.getPayload().length) {
                byte[] bytes = message.getPayload();
                this.seqNo -= bytes.length;
                node.logOutput("!");
                write(bytes, 0, bytes.length);
            }
        } else if (message.getType() == Transport.SYN || message.getType() == Transport.FIN) {
            if (this.lastAckReceived < message.getSeqNum() + 1) {
                node.logOutput("!");
                sendTransportPacket(message);
            }
        }
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
        return receiveBuffer.read(buf, pos, len);
    }

    /*
     * End of socket API
     */


    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
        if (this.state == State.CLOSED)
            return;

        if (this.sockType == SockType.CLIENT_CONNECTION) {
            if (this.seqNo + 1 == this.lastAckReceived) {
                this.release();
            }
            else {
                this.state = State.SHUTDOWN;
                addTimer(CLOSE_TIMEOUT, this, "close", null, null);
            }
        } else if(this.sockType == SockType.SERVER_CONNECTION) {
            if (this.receiveBuffer.isEmpty()) {
                this.release();
            }
            else {
                this.state = State.SHUTDOWN;
                addTimer(CLOSE_TIMEOUT, this, "close", null, null);
            }
        }
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        if (this.state == State.CLOSED)
            return;

        if (this.sockType == SockType.SERVER_CONNECTION) {
            this.state = State.CLOSED;
        } else if (this.sockType == SockType.CLIENT_CONNECTION) {
            this.seqNo++;
            Transport finPacket = new Transport(this.localPort, this.remotePort, Transport.FIN, 0, this.seqNo, new byte[0]);
            this.state = State.CLOSED;
            this.sendTransportPacket(finPacket);
            tcpManager.deregisterSock(this.localPort);
        } else {
            //shouldn't be here!
            node.logError("Release requested on a server socket! Noop.");
            return;
        }
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

    public int getLocalPort() {
        return localPort;
    }

    public State getState() {
        return state;
    }

    private void addTimer(long deltaT, Object object, String methodName, String[] paramTypes, Object[] params) {
        try {
            Method method = Callback.getMethod(methodName, object, paramTypes);
            Callback cb = new Callback(method, this, params);
            node.addTimer(cb, deltaT);
        }catch(Exception e) {
            node.logError("Failed to add timer callback. Method Name: " + methodName +
                    "\nException: " + e);
        }
    }

    private void updateLastAction() {
        this.lastAction = System.currentTimeMillis();
        node.logError("Updated last action for server socket with " + this.remoteAddr + ":" + this.remotePort + " to " + this.lastAction);
    }

    public long getLastAction() {
        return this.lastAction;
    }
}
