package com.example.zkfingerapp.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fingerprints")
public class FingerprintEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String userId;
    private byte[] templateData;
    private long createdAt;
    
    public FingerprintEntity(String userId, byte[] templateData, long createdAt) {
        this.userId = userId;
        this.templateData = templateData;
        this.createdAt = createdAt;
    }
    
    // Getters y Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public byte[] getTemplateData() { return templateData; }
    public void setTemplateData(byte[] templateData) { this.templateData = templateData; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
