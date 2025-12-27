package com.urlshortener.repository;

import com.urlshortener.entity.AiAnalysisCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AiAnalysisCacheRepository extends JpaRepository<AiAnalysisCache, Long> {

    Optional<AiAnalysisCache> findByUrlHash(String urlHash);

    @Modifying
    @Query("DELETE FROM AiAnalysisCache a WHERE a.expiresAt < :now")
    int deleteExpiredEntries(@Param("now") LocalDateTime now);

    boolean existsByUrlHashAndExpiresAtAfter(String urlHash, LocalDateTime now);
}

