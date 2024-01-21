package dev.pcm.omokgame.service;

import dev.pcm.omokgame.entity.UserEntity;
import dev.pcm.omokgame.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity saveUser(UserEntity user) {
        // 가입한 사용자가 있는지 체크
        Optional<UserEntity> result = userRepository.findByUserId(user.getUserId());
        if (result.isPresent()) {
            return null;
        }
        // 사용자가 없다면 저장
        return userRepository.save(user);
    }

    public UserEntity updateUser(UserEntity user) {
        // userId와 password가 있는지 체크
        Optional<UserEntity> result = userRepository.findByUserId(user.getUserId());
        result.get().setSessionid(user.getSessionid());
        // userId와 password가 있다면 업데이트
        return userRepository.save(result.get());
    }

    // userId와 password로 일치하는 사용자 찾기
    public UserEntity findByUserIdAndPassword(UserEntity user) {
        Optional<UserEntity> result = userRepository.findByUserIdAndPassword(user.getUserId(), user.getPassword());
        return result.orElse(null);
    }
}
