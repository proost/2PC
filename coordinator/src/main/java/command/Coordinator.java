package command;

import tasks.Voting;
import util.HostInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator {

    private Map<HostInfo, Boolean> replies;
    private Map<HostInfo, Boolean> acknowlegements;
    private ExecutorService executorService;
    private final List<HostInfo> hostInfoList;

    public static void main(String[] args) {

        List<HostInfo> hostInfoList = new ArrayList<>();
        hostInfoList.add(new HostInfo("localhost", 8080));
        hostInfoList.add(new HostInfo("localhost", 8081));

        Coordinator coordinator = new Coordinator(hostInfoList);
        coordinator.startTransaction();
    }

    public Coordinator(final List<HostInfo> hostInfoList) {
        // 멀티 쓰레드로 바꿀
        this.hostInfoList = hostInfoList;
        this.executorService = Executors.newFixedThreadPool(hostInfoList.size());
        for (HostInfo info : hostInfoList) {
            this.replies.put(info, false);
            this.acknowlegements.put(info, false);
        }
    }

    public void startTransaction() {
        if (hostInfoList.isEmpty()) throw new IllegalStateException("Can't start transaction without participants info");

        vote();
        //commit();
    }

    private void vote() {

        for (HostInfo info : hostInfoList) {
            executorService.submit(new Voting(info));
        }
    }

}
