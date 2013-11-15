import java.util.LinkedList;
import java.util.Queue;

public class SendBuffer {
    private int maxBufferSizeBytes;

    private int currentBufferSizeBytes;
    private Queue<Transport> buffer;

    public SendBuffer(int maxBufferSizeBytes) {
        this.maxBufferSizeBytes = maxBufferSizeBytes;
        this.currentBufferSizeBytes = 0;
        buffer = new LinkedList<Transport>();
    }

    public void setWindowSize(int sizeBytes) {
        maxBufferSizeBytes = sizeBytes;
    }

    public int append(Transport packet) { // Returns -1 is buffer is full; 0 otherwise
        if (currentBufferSizeBytes + packet.getPayload().length > maxBufferSizeBytes) {
            return -1;
        }

        currentBufferSizeBytes += packet.getPayload().length;
        buffer.add(packet);

        return 0;
    }

    public Transport poll() {
        if (currentBufferSizeBytes == 0)
            return null;

        currentBufferSizeBytes -= buffer.peek().getPayload().length;
        return buffer.poll();
    }

    public Transport peek() {
        return buffer.peek();
    }

    public boolean isEmpty() {
        if (buffer.isEmpty() && currentBufferSizeBytes != 0) {
            System.err.println("Counting error in receive buffer!");
        }

        return buffer.isEmpty();
    }

    public Queue<Transport> getBuffer() {
        return this.buffer;
    }

    public int getMaxBufferSizeBytes() {
        return maxBufferSizeBytes;
    }

    public boolean isFull(int addSize) {
        if (this.currentBufferSizeBytes + addSize >= this.maxBufferSizeBytes)
            return true;
        return false;
    }
}
