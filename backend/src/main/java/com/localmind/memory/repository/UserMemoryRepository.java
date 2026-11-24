package com.localmind.memory.repository;

import com.localmind.memory.entity.UserMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link UserMemory} (table {@code user_memories}).
 */
public interface UserMemoryRepository extends JpaRepository<UserMemory, String> {

    List<UserMemory> findByUserIdOrderByCreatedAtDesc(String userId);

    List<UserMemory> findByUserIdAndPinnedTrue(String userId);

    boolean existsByUserIdAndMemoryFact(String userId, String memoryFact);
}
