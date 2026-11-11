package com.localmind.memory.repository;

import com.localmind.memory.entity.UserMemory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemory, String> {

    List<UserMemory> findByUserIdOrderByCreatedAtDesc(String userId);

    List<UserMemory> findByUserIdAndPinnedTrue(String userId);

    Optional<UserMemory> findByIdAndUserId(String id, String userId);
}
