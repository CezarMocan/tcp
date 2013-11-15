import com.sun.tools.javac.util.Pair;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SocketSpace<K> implements ISocketSpace<K> {

    private static final long CLOSE_CONNECTION_TIME = 10000;
    private Map<K, TCPSock> sockSpace;
    private int maxSize;

    public SocketSpace() {
        this.sockSpace = new HashMap<K, TCPSock>();
        this.maxSize = -1;
    }

    public SocketSpace(int maxSize) {
        this();
        this.maxSize = maxSize;
    }

    @Override
    public int register(K key, TCPSock socket) { // Returns 0 on success, -1 on failure
        if (portBusy(key))
            return -1;
        if (sockSpace.keySet().size() == maxSize)
            return -1;

        sockSpace.put(key, socket);
        return 0;
    }

    @Override
    public int deregister(K key) { // Returns 0 on success, -1 on failure
        if (!portBusy(key))
            return -1;

        sockSpace.remove(key);
        return 0;
    }

    @Override
    public TCPSock get(K key) { // Returns the TCPSock bound to port, or null if there's nothing on that port
        if (!portBusy(key))
            return null;

        return sockSpace.get(key);
    }

    @Override
    public boolean portBusy(K key) { // Returns true if port is used, 0 otherwise
        if (sockSpace.containsKey(key))
            return true;
        return false;
    }

    @Override
    public Pair<K, TCPSock> pop() { // Returns first connection in the space; null if space is empty
        if (sockSpace.isEmpty())
            return null;

        //TODO: make this safer
        K key = sockSpace.keySet().iterator().next();
        return new Pair<K, TCPSock>(key, sockSpace.remove(key));
    }

    @Override
    public int size() {
        return sockSpace.size();
    }

    @Override
    public void cleanup() {
        //System.err.println("cleanup() called");
        Iterator <Map.Entry<K, TCPSock>> iterator = sockSpace.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, TCPSock> value = iterator.next();
            if (value.getValue().isClosed())
                iterator.remove();
            else if (System.currentTimeMillis() - value.getValue().getLastAction() > CLOSE_CONNECTION_TIME) {
                value.getValue().close();
                iterator.remove();
            }
        }
    }
}
