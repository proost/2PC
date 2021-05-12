package coordinator;

import commons.CommitPhaseState;
import commons.CommitRequestPhaseState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import participant.Participant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class Coordinator {

    @Getter private volatile CommitRequestPhaseState commitRequestPhaseState;
    @Getter private volatile CommitPhaseState commitPhaseState;

    private final Participant participant;
    private final int numOfParticipants;

    private final Semaphore sem;
    private final ExecutorService executors;

    private final CountDownLatch votingStart;
    private final CountDownLatch votingEnd;
    private final CountDownLatch operationStart;
    private final CountDownLatch operationEnd;
    private final CountDownLatch transactionWrapup;

    private List<Boolean> votes;
    private List<Boolean> acks;

    public static void main(String[] args) {
        Coordinator coordinator = new Coordinator(3 );

        coordinator.startTransaction();
    }

    public Coordinator(final int numOfParticipants) {
        configLogging();

        this.numOfParticipants = numOfParticipants;

        // configure overall process
        this.sem = new Semaphore(1); // Only one transaction allowed
        this.executors = Executors.newFixedThreadPool(numOfParticipants);
        this.votes = new ArrayList<>(); // vote commit or abort
        this.acks = new ArrayList<>(); // acknowledge children process after completion process end

        // configure participants process
        this.votingStart = new CountDownLatch(1);
        this.votingEnd = new CountDownLatch(this.numOfParticipants);
        this.operationStart = new CountDownLatch(1);
        this.operationEnd = new CountDownLatch(this.numOfParticipants);
        this.transactionWrapup = new CountDownLatch(1);
        this.participant = new Participant(this, votingStart, operationStart, transactionWrapup);
    }

    private void configLogging() {
        MDC.put("className", "coordinator");
    }

    public void startTransaction() {
        try {
            this.sem.acquire();

            initParticipants();
        } catch (InterruptedException e) {
            log.error(e.getMessage());

            throw new IllegalThreadStateException("Interrupted starting transaction");
        }

        final boolean votingResult = startVotingPhase();
        if (isAbleToOperate(votingResult)) {
            commitRequestPhaseState = CommitRequestPhaseState.SUCCESS;

            startOperation();
        }

        endTransaction();

        this.sem.release();
    }

    private void initParticipants() {
        for (int i=0; i<numOfParticipants; i++) {
            executors.execute(participant);
        }
    }

    /*
    Commit request (or voting) phase
        1. The coordinator sends a query to commit message to all participants and waits until it has received a reply from all participants.
    */
    private boolean startVotingPhase() {
        log.info("Commit request phase(=voting phase)");

        votingStart.countDown();

        try {
            return votingEnd.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage());

            throw new IllegalThreadStateException("Interrupted Voting phase");
        }
    }

    public synchronized void vote(final boolean voting) {
        votes.add(voting);

        votingEnd.countDown();
    }

    private boolean isAbleToOperate(final boolean votingResult) {
        return votes.stream().allMatch(v -> v) // check all agreements is yes
                && votingResult // If some participant is timeout, can't start commit phase
                && votes.size() == numOfParticipants;
    }

    /*
    Commit (or completion) phase
        Success
        If the coordinator received an agreement message from all participants during the commit-request phase:
            1. The coordinator sends a commit message to all the participants.
            4. The coordinator completes the transaction when all acknowledgments have been received.

        Failure
        If any participant votes No during the commit-request phase (or the coordinator's timeout expires):
            1. The coordinator sends a rollback message to all the participants.
            4. The coordinator undoes the transaction when all acknowledgements have been received.
     */
    private void startOperation() {
        operationStart.countDown();

        try {
            operationEnd.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            commitPhaseState = CommitPhaseState.UNDO;
        }
    }

    public synchronized void acknowledge(final boolean ack) {
        acks.add(ack);

        operationEnd.countDown();
    }

    private void endTransaction() {
        if (acks.stream().allMatch(v -> v)  && acks.size() == numOfParticipants) {
            commitPhaseState = CommitPhaseState.COMMIT;
        } else {
            commitPhaseState = CommitPhaseState.UNDO;
        }

        transactionWrapup.countDown();
    }
}
