package com.eos.lss.repository;

import com.eos.lss.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import com.eos.lss.entity.SessionStatus;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {
    Optional<Session> findByEntryCode(String entryCode);
    List<Session> findByStatus(SessionStatus status);
} 