package com.sxw.sxwaiagent.treehole;

import com.sxw.sxwaiagent.agent.hermes.HermesAgent;
import com.sxw.sxwaiagent.agent.hermes.HermesReply;
import com.sxw.sxwaiagent.treehole.dto.CreateTreeholeRequest;
import com.sxw.sxwaiagent.treehole.dto.TreeholeResponse;
import com.sxw.sxwaiagent.treehole.model.TreeholeEntry;
import com.sxw.sxwaiagent.treehole.repository.TreeholeEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TreeholeService {

    private final TreeholeEntryRepository treeholeEntryRepository;
    private final HermesAgent hermesAgent;

    public TreeholeService(TreeholeEntryRepository treeholeEntryRepository, HermesAgent hermesAgent) {
        this.treeholeEntryRepository = treeholeEntryRepository;
        this.hermesAgent = hermesAgent;
    }

    @Transactional
    public TreeholeResponse create(Long userId, CreateTreeholeRequest request) {
        HermesReply reply = hermesAgent.comfort(request.content(), "treehole-" + userId);
        TreeholeEntry entry = TreeholeEntry.create(
                userId,
                request.title().trim(),
                request.content().trim(),
                emptyToDefault(reply.emotionTag(), "unknown"),
                emptyToDefault(reply.summary(), request.content().trim()),
                emptyToDefault(reply.reply(), "")
        );
        return TreeholeResponse.from(treeholeEntryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public List<TreeholeResponse> list(Long userId) {
        return treeholeEntryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TreeholeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TreeholeResponse get(Long userId, Long entryId) {
        TreeholeEntry entry = treeholeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("treehole not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("treehole not found");
        }
        return TreeholeResponse.from(entry);
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        TreeholeEntry entry = treeholeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("treehole not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("treehole not found");
        }
        treeholeEntryRepository.delete(entry);
    }

    private static String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
