package com.edubase.auth.service.concretes;


import com.edubase.auth.dto.RegisterRequest;
import com.edubase.auth.entity.User;
import com.edubase.auth.repository.UserRepository;
import com.edubase.common.exceptions.BusinessException;
import com.edubase.common.handling.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationServiceImpl {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public Long register(RegisterRequest registerRequest) {
        log.info("Yeni kullanıcı kaydı isteği: {}", registerRequest.getEmail());

        if(userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());

        User user = User.builder()
                .email(registerRequest.getEmail())
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .passwordHash(encodedPassword)
                .isActive(true)
                .build();
        userRepository.save(user);
        log.info("Kullanıcı başarıyla oluşturuldu. ID: {}", user.getId());
        return user.getId();
    }

}
