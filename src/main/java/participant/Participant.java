package participant;

import commons.CommitPhaseState;
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
    private final CountDownLatch completionStart;

    private CommitPhaseState state;

    public Participant(
            final Coordinator coordinator,
            final CountDownLatch votingStart,
            final CountDownLatch completionStart
    ) {
        this.coordinator = coordinator;
        this.votingStart = votingStart;
        this.completionStart = completionStart;
    }

    @Override
    public void run() {
        MDC.put("className", "participant");

        try {
            votingStart.await();

            // This lock means the resources that held during the transaction.
            Lock lock = new ReentrantLock();
            lock.lock();

            // voteNo(); -> Maybe, occur when locking resource fails
            voteYes();

            try {
                final boolean isConnected = completionStart.await(10, TimeUnit.SECONDS);

                if (isConnected) {
                    if (state == CommitPhaseState.SUCCESS) {
                        log.info("-----redo log-----");

                        commit();
                    } else {
                        log.info("-----undo log-----");

                        rollback();
                    }

                    sendAck();
                } else {
                    throw new IllegalStateException("Coordinator response Timeout");
                }
            } catch (InterruptedException | IllegalStateException e) {
                // Interrupted during commit phase or Timeout
                rollback();

                sendAck();
            }

            lock.unlock();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.error("Interrupted during waiting vote phase");
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
    public void setCommitPhaseState(final CommitPhaseState state) {
        this.state = state;
    }

    private void commit() {
        log.info("Commit");
    }

    private void rollback() {
        log.info("Rollback");
    }

    private void sendAck() {
        coordinator.acknowledge(true);
    }
}

