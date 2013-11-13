/*
Interface for socket space implementations;
The socket space needs to have 3 methods:
    1. register - called when a new server socket or client connection socket is created
    2. get - called when a packet's connection socket is determined by its source port
    3. deregister - called when a socket is closed
    4. portBusy - boolean, returns true if given port is used
    5. pop - removes a random connection from the socket space and returns it
 */

import com.sun.tools.javac.util.Pair;

public interface ISocketSpace<K> {
    public int register(K key, TCPSock socket);
    public int deregister(K key);
    public TCPSock get(K key);
    public boolean portBusy(K key);
    public Pair<K, TCPSock> pop();
    public int size();
    public void cleanup();
}
