package com.sxw.sxwaiagent.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TraceReplayService {
    
    private static final Logger log = LoggerFactory.getLogger(TraceReplayService.class);
    
    private final TraceRepository traceRepository;
    
    public TraceReplayService(TraceRepository traceRepository) {
        this.traceRepository = traceRepository;
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
        
        if (traceOpt.isEmpty()) {
            log.warn("Trace not found: {}", traceId);
            return new TraceReplayResult(false, null, "Trace not found");
        }
        
        TraceRecord trace = traceOpt.get();
        
        // TODO: Actually replay the trace by re-executing with same input
        // For now, just return the original trace data
        
        log.info("Replaying trace: {}", traceId);
        
        return new TraceReplayResult(
            true,
            trace,
            "Replay completed (original trace returned)"
        );
    }
    
    public record TraceReplayResult(
        boolean success,
        TraceRecord trace,
        String message
    ) {}
}
