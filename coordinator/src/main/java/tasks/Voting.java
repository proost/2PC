package tasks;

import util.HostInfo;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Callable;

public class Voting implements Callable<Boolean> {

    private static final String MESSAGE = "VOTE";

    private final HostInfo hostInfo;
    private final Socket socket;

    public Voting(final HostInfo hostInfo) {
        this.hostInfo = hostInfo;

        try {
            socket = new Socket(hostInfo.getIp(), hostInfo.getPort());
        } catch (IOException e) {
            throw new IllegalThreadStateException("Thread stop Because of Connection Error");
        }
    }

    @Override
    public Boolean call() {
        try (OutputStream output = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(output, true);
             InputStream input = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))
        ) {
            writer.println(MESSAGE);

            if (reader.readLine() != null) {
                socket.close();

                return true;
            } else {
               throw new IllegalStateException("Can't start transaction");
            }
        } catch (IOException e) {
            throw new IllegalThreadStateException("Thread stop Because of Disconnection Error");
        }
    }
}
