package dev.pcm.omokgame.repository;

import dev.pcm.omokgame.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUserId(String userId);
    Optional<UserEntity> findByUserIdAndPassword(String userId, String password);
}
