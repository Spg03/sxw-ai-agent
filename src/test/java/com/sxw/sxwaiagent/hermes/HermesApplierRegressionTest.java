package com.sxw.sxwaiagent.hermes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.knowledge.DocumentIngestService;
import com.sxw.sxwaiagent.evaluation.EvalService;
import com.sxw.sxwaiagent.memory.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HermesApplierRegressionTest {
    private HermesCandidate candidate(CandidateType type, HermesCandidate.CandidateStatus status) {
        return new HermesCandidate("candidate-1","run","chat",type,"title","content",null,status,"admin",Instant.now(),Instant.now(),"run",null);
    }

    @Test void failedKnowledgeIngestionMustNotBeReportedAsApplied() {
        var ingest=mock(DocumentIngestService.class);
        when(ingest.ingest("[Hermes] title","hermes://candidate-1","content"))
                .thenReturn(new DocumentIngestService.IngestResult(null,0,"embedding failed",null));
        var applier=new HermesApplier(mock(MemoryService.class),ingest,mock(EvalService.class),new ObjectMapper());
        assertThrows(IllegalStateException.class,()->applier.apply(candidate(CandidateType.KNOWLEDGE,HermesCandidate.CandidateStatus.APPROVED)));
    }

    @Test void retriedMemoryUsesStableIdAndCannotBypassApproval() {
        var repository=mock(MemoryRepository.class);
        var memory=new MemoryService(repository,mock(MemoryIndex.class),mock(MemorySelector.class));
        var applier=new HermesApplier(memory,mock(DocumentIngestService.class),mock(EvalService.class),new ObjectMapper());
        assertThrows(IllegalStateException.class,()->applier.apply(candidate(CandidateType.MEMORY,HermesCandidate.CandidateStatus.PENDING)));
        verifyNoInteractions(repository);
        var approved=candidate(CandidateType.MEMORY,HermesCandidate.CandidateStatus.APPROVED);
        String first=applier.apply(approved);
        String second=applier.apply(approved);
        assertEquals(first,second);
        var captures=org.mockito.ArgumentCaptor.forClass(MemoryItem.class);
        verify(repository,times(2)).save(captures.capture());
        assertEquals(captures.getAllValues().get(0).memoryId(),captures.getAllValues().get(1).memoryId());
    }
}
