package com.example.system.model;

public class DocumentChunk {

    private String id;
    private String documentId;
    private int chunkIndex;
    private int startOffset;
    private int endOffset;
    private String content;
    private double[] vector;

    public DocumentChunk() {
    }

    public DocumentChunk(String id, String documentId, int chunkIndex, int startOffset,
            int endOffset, String content, double[] vector) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.content = content;
        this.vector = vector;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double[] getVector() {
        return vector;
    }

    public void setVector(double[] vector) {
        this.vector = vector;
    }
}
