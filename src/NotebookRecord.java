package com.jianyi.yixue.app;

/** Portable notebook data, independent of Android UI and database APIs. */
public final class NotebookRecord {
    public String id = "", title = "", question = "", analysis = "", feedback = "", tags = "";
    public String method = "", info = "", sourceCaseId = "";
    public int upper, lower, moving, revision = 1;
    public long createdAt, updatedAt;
    public boolean favorite;

    public NotebookRecord copy() {
        NotebookRecord r = new NotebookRecord();
        r.id = id; r.title = title; r.question = question; r.analysis = analysis;
        r.feedback = feedback; r.tags = tags; r.method = method; r.info = info;
        r.sourceCaseId = sourceCaseId; r.upper = upper; r.lower = lower; r.moving = moving;
        r.revision = revision; r.createdAt = createdAt; r.updatedAt = updatedAt; r.favorite = favorite;
        return r;
    }
}
