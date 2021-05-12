package participant;

import commons.CommitPhaseState;
import commons.CommitRequestPhaseState;
import coordinator.Coordinator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


@Slf4j
public class Participant implements Runnable {

    private final Coordinator coordinator;
    private final CountDownLatch votingStart;
    private final CountDownLatch operationStart;
    private final CountDownLatch transactionWrapup;

    public Participant(final Coordinator coordinator, final CountDownLatch votingStart,
                       final CountDownLatch operationStart, final CountDownLatch transactionWrapup
    ) {
        this.coordinator = coordinator;
        this.votingStart = votingStart;
        this.operationStart = operationStart;
        this.transactionWrapup = transactionWrapup;
    }

    @Override
    public void run() {
        MDC.put("className", "participant");

        try {
            votingStart.await();
        } catch (InterruptedException e) {
            log.error("Interrupted during waiting vote phase");

            Thread.currentThread().interrupt();
        }

        // This lock means the resources that held during the transaction.
        Lock lock = new ReentrantLock();
        lock.lock();

        // voteNo(); -> Maybe, occur when locking resource fails
        voteYes();

        try {
            operationStart.await(5, TimeUnit.SECONDS);

            if (coordinator.getCommitRequestPhaseState() == CommitRequestPhaseState.SUCCESS) {
                operate();

                sendAck(true);
            } else {
                sendAck(false);
            }
        } catch (Exception e) {
            // Interrupted by Timeout or operation failed

            sendAck(false);
        } finally {
            lock.unlock();
        }

        try {
            transactionWrapup.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (coordinator.getCommitPhaseState() == CommitPhaseState.COMMIT) {
            commit();
        } else {
            rollback();
        }
    }

    /*
    Commit request (or voting) phase
        2. The participants execute the transaction up to the point where they will be asked to commit. They each write an entry to their undo log and an entry to their redo log.
        3. Each participant replies with an agreement message (participant votes Yes to commit), if the participant's actions succeeded, or an abort message (participant votes No, not to commit), if the participant experiences a failure that will make it impossible to commit.
    */
    private void voteYes() {
        log.info("Vote Yes");

        coordinator.vote(true);
    }

    private void voteNo() {
        log.info("Vote No");

        coordinator.vote(false);
    }

    /*
    Commit (or completion) phase
        Success
        If the coordinator received an agreement message from all participants during the commit-request phase:
            2. Each participant completes the operation, and releases all the locks and resources held during the transaction.
            3. Each participant sends an acknowledgement to the coordinator.

        Failure
        If any participant votes No during the commit-request phase (or the coordinator's timeout expires):
            2. Each participant undoes the transaction using the undo log, and releases the resources and locks held during the transaction.
            3. Each participant sends an acknowledgement to the coordinator.
     */
    private void operate() {
        log.info("Do job");
    }

    private void commit() {
        log.info("Commit");
    }

    private void rollback() {
        log.info("Rollback");
    }

    private void sendAck(boolean ack) {
        coordinator.acknowledge(ack);
    }
}

