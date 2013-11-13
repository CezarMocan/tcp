import java.util.LinkedList;
import java.util.Queue;

public class SendBuffer {
    private int maxBufferSize;
    private int currentBufferSize;
    private Queue<Transport> buffer;

    public SendBuffer(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
        this.currentBufferSize = 0;
        buffer = new LinkedList<Transport>();
    }

    public int append(Transport packet) { // Returns -1 is buffer is full; 0 otherwise
        if (currentBufferSize + 1 > maxBufferSize) {
            return -1;
        }

        currentBufferSize++;
        buffer.add(packet);

        return 0;
    }

    public Transport poll() {
        if (currentBufferSize == 0)
            return null;

        currentBufferSize--;
        return buffer.poll();
    }

    public Transport peek() {
        return buffer.peek();
    }

    public boolean isEmpty() {
        if (buffer.isEmpty() && currentBufferSize != 0) {
            System.err.println("Counting error in receive buffer!");
        }

        return buffer.isEmpty();
    }

    public Queue<Transport> getBuffer() {
        return this.buffer;
    }

    public boolean isFull() {
        if (this.currentBufferSize == this.maxBufferSize)
            return true;
        return false;
    }
}
