package com.example.zkfingerapp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface FingerprintDao {
    @Insert
    long insert(FingerprintEntity fingerprint);
    
    @Query("SELECT * FROM fingerprints")
    List<FingerprintEntity> getAll();
    
    @Query("SELECT * FROM fingerprints WHERE userId = :userId")
    FingerprintEntity getByUserId(String userId);
    
    @Query("DELETE FROM fingerprints WHERE userId = :userId")
    int deleteByUserId(String userId);
    
    @Query("DELETE FROM fingerprints")
    void clearAll();
    
    @Query("SELECT COUNT(*) FROM fingerprints")
    int getCount();
}
