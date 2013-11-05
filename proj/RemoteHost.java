public final class RemoteHost {

    private final int address;
    private final int port;

    public RemoteHost(int address, int port) {
        this.address = address;
        this.port = port;
    }

    public int getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoteHost)) return false;

        RemoteHost that = (RemoteHost) o;

        if (address != that.address) return false;
        if (port != that.port) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = address;
        result = 31 * result + port;
        return result;
    }
}
