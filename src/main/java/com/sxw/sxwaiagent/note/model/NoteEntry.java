package com.sxw.sxwaiagent.note.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "ai_note", uniqueConstraints = @UniqueConstraint(name = "uk_ai_note_user_title", columnNames = {"user_id", "title"}))
public class NoteEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tags = "";

    @Column(nullable = false)
    private boolean favorite;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected NoteEntry() {
    }

    public static NoteEntry create(Long userId, String title, String content, String tags) {
        NoteEntry note = new NoteEntry();
        note.userId = userId;
        note.title = title;
        note.content = content;
        note.tags = tags;
        return note;
    }

    public void update(String title, String content, String tags, boolean favorite) {
        this.title = title;
        this.content = content;
        this.tags = tags;
        this.favorite = favorite;
    }

    public void append(String additionalContent) {
        this.content = this.content.isBlank() ? additionalContent : this.content + System.lineSeparator() + additionalContent;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getTags() { return tags; }
    public boolean isFavorite() { return favorite; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
