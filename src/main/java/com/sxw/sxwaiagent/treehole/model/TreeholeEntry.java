package com.sxw.sxwaiagent.treehole.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sxw_treeholes")
public class TreeholeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 64)
    private String emotionTag;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String hermesSummary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String hermesReply;

    @Column(nullable = false)
    private Instant createdAt;

    protected TreeholeEntry() {
    }

    public static TreeholeEntry create(Long userId,
                                       String title,
                                       String content,
                                       String emotionTag,
                                       String hermesSummary,
                                       String hermesReply) {
        TreeholeEntry entry = new TreeholeEntry();
        entry.userId = userId;
        entry.title = title;
        entry.content = content;
        entry.emotionTag = emotionTag;
        entry.hermesSummary = hermesSummary;
        entry.hermesReply = hermesReply;
        return entry;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getEmotionTag() {
        return emotionTag;
    }

    public String getHermesSummary() {
        return hermesSummary;
    }

    public String getHermesReply() {
        return hermesReply;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
