import java.util.LinkedList;
import java.util.Queue;

public class ReceiveBuffer {
    private int maxBufferSize;
    private int currentBufferSize;
    private Queue<Byte> buffer;

    public ReceiveBuffer(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
        this.currentBufferSize = 0;
        buffer = new LinkedList<Byte>();
    }

    public int append(byte[] bytes) { // Returns -1 is buffer is full; 0 otherwise
        if (currentBufferSize + bytes.length > maxBufferSize) {
            return -1;
        }

        currentBufferSize += bytes.length;
        for (int i = 0; i < bytes.length; i++)
            buffer.add(bytes[i]);

        return 0;
    }

    public Byte poll() {
        if (currentBufferSize == 0)
            return null;
        currentBufferSize--;
        return buffer.poll();
    }

    public int read(byte[] bytes, int index, int len) {
        int bytesCount = 0;
        for (int i = index; i < index + len && currentBufferSize > 0; i++) {
            bytes[i] = poll();
            bytesCount++;
        }

        return bytesCount;
    }

    public boolean isEmpty() {
        if (buffer.isEmpty() && currentBufferSize != 0) {
            System.err.println("Counting error in receive buffer!");
        }
        return buffer.isEmpty();
    }

    public int getWindowSize() {
        return maxBufferSize - currentBufferSize;
    }
}
