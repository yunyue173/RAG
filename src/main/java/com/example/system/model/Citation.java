package com.example.system.model;

public class Citation {

    private String chunkId;
    private int chunkIndex;
    private String content;
    private double score;

    public Citation() {
    }

    public Citation(String chunkId, int chunkIndex, String content, double score) {
        this.chunkId = chunkId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.score = score;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
