package tasks;

import command.Coordinator;
import util.HostInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;

public class Voting implements Callable<HostInfo> {

    private final HostInfo hostInfo;
    private final Socket socket;

    public Voting(HostInfo hostInfo) {
        this.hostInfo = hostInfo;

        try {
            socket = new Socket(hostInfo.getIp(), hostInfo.getPort());
        } catch (IOException e) {
            throw new IllegalThreadStateException("Thread stop Because of Connection Error");
        }
    }

    @Override
    public HostInfo call() {
        try (OutputStream output = socket.getOutputStream();
             InputStream input = socket.getInputStream()
        ) {


            socket.close();

            return hostInfo;
        } catch (IOException e) {
            throw new IllegalThreadStateException("Thread stop Because of Disconnection Error");
        }
    }
}
