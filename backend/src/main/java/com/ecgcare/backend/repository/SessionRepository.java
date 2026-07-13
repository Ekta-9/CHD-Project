package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findBySessionIdAndLogoutAtIsNull(UUID sessionId);

    @Query("SELECT s FROM Session s WHERE s.doctor.doctorId = :doctorId AND s.logoutAt IS NULL ORDER BY s.loginAt DESC")
    List<Session> findActiveSessionsByDoctorId(@Param("doctorId") UUID doctorId);

    @Modifying
    @Query("UPDATE Session s SET s.logoutAt = :logoutAt, s.endedBy = :reason WHERE s.sessionId = :sessionId")
    void logoutSession(@Param("sessionId") UUID sessionId, @Param("logoutAt") OffsetDateTime logoutAt,
            @Param("reason") Session.SessionEndReason reason);

    @Modifying
    @Query("UPDATE Session s SET s.lastActivityAt = :activityAt WHERE s.sessionId = :sessionId")
    void updateLastActivity(@Param("sessionId") UUID sessionId, @Param("activityAt") OffsetDateTime activityAt);
}









