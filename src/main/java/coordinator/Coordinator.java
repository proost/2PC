package coordinator;

import commons.CommitPhaseState;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import participant.Participant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class Coordinator {

    private final Participant participant;
    private final int numOfParticipants;

    private final Semaphore sem;
    private final CountDownLatch timer;
    private final ExecutorService executors;

    private final CountDownLatch votingStart;
    private final CountDownLatch votingEnd;
    private final CountDownLatch completionStart;
    private final CountDownLatch completionEnd;

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
        this.timer = new CountDownLatch(numOfParticipants); // wait overall process or timeout overall process(= trasaction fail)
        this.votes = new ArrayList<>(); // vote commit or abort
        this.acks = new ArrayList<>(); // acknowledge children process after completion process end

        // configure participants process
        this.votingStart = new CountDownLatch(1);
        this.votingEnd = new CountDownLatch(this.numOfParticipants);
        this.completionStart = new CountDownLatch(1);
        this.completionEnd = new CountDownLatch(this.numOfParticipants);
        this.participant = new Participant(this, votingStart, completionStart);
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

        final boolean isAllParticipantsVote = startVotingPhase();
        final boolean isAllParticipantsAcknowledge = startCompletionPhase(isAllParticipantsVote);

        endTransaction(isAllParticipantsAcknowledge);

        try {
            final boolean isTimeout = !timer.await(5, TimeUnit.SECONDS);

            logTimeout(isTimeout);
        } catch (InterruptedException e) {
            throw new IllegalThreadStateException("Interrupted finishing transaction");
        }

        this.sem.release();

        this.executors.shutdown(); // Close thread pool
    }

    private void initParticipants() {
        for (int i=0; i<numOfParticipants; i++) {
            executors.execute(() -> {
                participant.run();

                timer.countDown();
            });
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
    private boolean startCompletionPhase(final boolean isAllParticipantsVote) {
        if (isAbleToCommit(isAllParticipantsVote)) {
            log.info("Commit (or completion) phase - Success");

            startCommit();
        } else {
            log.info("Commit (or completion) phase - Fail");

            abortCommit();
        }

        try {
            return completionEnd.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalThreadStateException("Interrupted Completion phase");
        }
    }

    private boolean isAbleToCommit(final boolean isAllParticipantsVote) {
        return votes.stream().allMatch(v -> v) // check all agreements is yes
                && isAllParticipantsVote // If some participant is timeout, can't start commit phase
                && votes.size() == numOfParticipants;
    }

    private void startCommit() {
        participant.setCommitPhaseState(CommitPhaseState.SUCCESS);

        completionStart.countDown();
    }

    private void abortCommit() {
        participant.setCommitPhaseState(CommitPhaseState.FAILURE);

        completionStart.countDown();
    }

    public synchronized void acknowledge(final boolean ack) {
        acks.add(ack);

        completionEnd.countDown();
    }

    private void endTransaction(final boolean isAllParticipantsAcknowledge) {
        if (acks.stream().allMatch(v -> v) // Maybe some participants not acknowledge
            && isAllParticipantsAcknowledge // If some participants are timeout during commit phase
            && acks.size() == numOfParticipants
        ) {
            log.info("End transaction");
        } else {
            log.error("Receiving acknowledgement fails");
        }
    }

    private void logTimeout(final boolean isTimeout) {
        if (isTimeout) {
            log.error("Transaction timeout");
        }
    }
}
