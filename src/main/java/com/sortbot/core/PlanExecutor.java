package com.sortbot.core;

import com.sortbot.domain.FileAction;
import com.sortbot.domain.Plan;
import com.sortbot.domain.RunResult;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Invoker in the Command pattern (F06): runs an approved plan and logs every action. */
public class PlanExecutor extends ProgressPublisher {
    private final ActionLog log;
    private final TraceLogger trace;
    private volatile boolean cancelRequested;

    public PlanExecutor(ActionLog log, TraceLogger trace) {
        this.log = log;
        this.trace = trace;
    }

    /** Stops the current run after the action in progress. Completed actions stay logged and undoable. */
    public void cancel() {
        cancelRequested = true;
    }

    public RunResult execute(Plan plan) {
        if (!plan.isApproved()) throw new IllegalStateException("The plan has not been approved.");
        cancelRequested = false;
        String runId = log.newRunId();
        List<String> messages = new ArrayList<>();
        int done = 0, failed = 0, skipped = 0, seq = 0;
        boolean stop = false;
        int total = plan.size();

        for (FileAction action : plan.getActions()) {
            if (stop || cancelRequested) {
                skipped++;
                continue;
            }
            publish(new ProgressEvent("execute", seq + 1, total, action.describe()));
            ActionStatus status;
            String message = "";
            try {
                action.execute();
                status = ActionStatus.DONE;
                done++;
            } catch (NotAuthorizedException | FileSystemException e) {
                status = ActionStatus.FAILED;
                message = e.getMessage() == null ? e.toString() : e.getMessage();
                failed++;
                messages.add(action.describe() + ": " + message);
            } catch (IOException e) {
                status = ActionStatus.FAILED;
                message = "Stopped after an I/O error: " + e.getMessage();
                failed++;
                stop = true;
                messages.add(action.describe() + ": " + message);
            }
            String src = action.source() == null ? "" : action.source().toString();
            String tgt = status == ActionStatus.DONE ? action.resultTarget().toString() : action.target().toString();
            log.append(new ActionEntry(runId, seq++, action.type(), src, tgt,
                    status == ActionStatus.DONE && action.created(), status, message, Instant.now()));
            trace.fileChange(action.type(), src, tgt, status.name());
        }
        return new RunResult(runId, done, failed, skipped, cancelRequested, messages);
    }
}
