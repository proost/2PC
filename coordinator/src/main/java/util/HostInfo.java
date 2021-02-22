package util;

import lombok.Getter;

@Getter
public class HostInfo {

    private final String ip;
    private final int port;
    private int hashCode;

    public HostInfo(final String ip, final int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;

        if (!(o instanceof HostInfo)) return false;

        HostInfo hostInfo = (HostInfo) o;

        return ip.equals(hostInfo.ip) && (port == hostInfo.port);
    }

    @Override
    public int hashCode() {
        int result = hashCode;

        if (result == 0) {
            result = ip.hashCode();
            result = 31 * result + Integer.hashCode(port);

            hashCode = result;
        }

        return result;
    }
}
