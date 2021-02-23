package command;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import tasks.Voting;
import util.HostInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
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

        try {
            vote();
        } catch(IllegalThreadStateException | IllegalStateException e) {
            log.error(e.getMessage());
        }
        //commit();
    }

    private void vote() {
        List<Future<Boolean>> futures = new ArrayList<>();

        for (HostInfo info : hostInfoList) {
            futures.add(executorService.submit(new Voting(info)));
        }

        List<Boolean> result = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            try {
                result.add(future.get(1, TimeUnit.SECONDS));
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new IllegalThreadStateException(e.getMessage());
            }
        }

        if (result.stream().anyMatch(r -> !r)) {
            throw new IllegalStateException("Can't proceed");
        }
    }

}
