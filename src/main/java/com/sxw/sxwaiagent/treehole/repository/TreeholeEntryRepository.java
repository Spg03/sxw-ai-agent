package com.sxw.sxwaiagent.treehole.repository;

import com.sxw.sxwaiagent.treehole.model.TreeholeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TreeholeEntryRepository extends JpaRepository<TreeholeEntry, Long> {

    List<TreeholeEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
}
