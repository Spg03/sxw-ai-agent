package com.sxw.sxwaiagent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import com.sxw.sxwaiagent.memory.AgentRunSnapshotService;

@Service
public class TraceReplayService {
    
    private static final Logger log = LoggerFactory.getLogger(TraceReplayService.class);
    
    private final TraceRepository traceRepository;
    private final AgentRunSnapshotService snapshots;
    
    public TraceReplayService(TraceRepository traceRepository, AgentRunSnapshotService snapshots) {
        this.traceRepository = traceRepository;
        this.snapshots = snapshots;
    }
    
    public Optional<TraceRecord> getTrace(String traceId) {
        return traceRepository.findByTraceId(traceId);
    }
    
    public List<TraceRecord> getRecentTraces(int limit) {
        return traceRepository.findRecent(limit);
    }
    
    public List<TraceRecord> getTracesByAgentType(String agentType, int limit) {
        return traceRepository.findByAgentType(agentType, limit);
    }
    
    public TraceReplayResult replay(String traceId) {
        Optional<TraceRecord> traceOpt = traceRepository.findByTraceId(traceId);
        log.info("Replaying trace: {}", traceId);
        try {
            AgentRunSnapshotService.ReplayInput input = snapshots.replayInputByTraceId(traceId);
            return new TraceReplayResult(input.hashVerified(), traceOpt.orElse(null), input,
                    input.hashVerified()
                            ? "Equivalent model input reconstructed and SHA-256 verified (call " + input.callNo() + ")"
                            : "Snapshot hash verification failed");
        } catch (IllegalArgumentException noSnapshot) {
            if (traceOpt.isEmpty()) {
                log.warn("Trace and replay snapshot not found: {}", traceId);
                return new TraceReplayResult(false, null, null, "Trace not found");
            }
            return new TraceReplayResult(false, traceOpt.get(), null,
                    "Legacy trace has no context snapshot and cannot be replayed safely");
        }
    }
    
    public record TraceReplayResult(
        boolean success,
        TraceRecord trace,
        AgentRunSnapshotService.ReplayInput replayInput,
        String message
    ) {}
}
