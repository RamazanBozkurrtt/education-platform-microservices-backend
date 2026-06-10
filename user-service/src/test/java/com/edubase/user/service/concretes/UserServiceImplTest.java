package com.edubase.user.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.configuration.mapper.UserMapper;
import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getMe_shouldUseAuthUserId_whenProfileExists() {
        Long authUserId = 42L;
        UserProfile profile = UserProfile.builder()
                .email("user@example.com")
                .authUserId(authUserId)
                .build();
        UserProfileResponse response = UserProfileResponse.builder().email("user@example.com").build();

        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(profile));
        when(userMapper.toResponseFromEntity(profile)).thenReturn(response);

        UserProfileResponse result = userService.getMe(authUserId, "user@example.com");

        assertEquals("user@example.com", result.getEmail());
        verify(userProfileRepository).findByAuthUserId(authUserId);
        verify(userProfileRepository, never()).findByEmailIgnoreCase(any());
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void getMe_shouldFallbackToEmailAndBackfillAuthUserId_whenLegacyRecordExists() {
        Long authUserId = 84L;
        UserProfile legacyProfile = UserProfile.builder()
                .email("legacy@example.com")
                .build();
        UserProfileResponse response = UserProfileResponse.builder().email("legacy@example.com").build();

        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());
        when(userProfileRepository.findByEmailIgnoreCase("legacy@example.com")).thenReturn(Optional.of(legacyProfile));
        when(userMapper.toResponseFromEntity(legacyProfile)).thenReturn(response);

        UserProfileResponse result = userService.getMe(authUserId, "legacy@example.com");

        assertEquals("legacy@example.com", result.getEmail());
        assertEquals(authUserId, legacyProfile.getAuthUserId());
        verify(userProfileRepository).save(legacyProfile);
    }

    @Test
    void updateMe_shouldNotChangeEmail_butShouldUpdateProfileFields() {
        Long authUserId = 99L;
        UserProfile existing = UserProfile.builder()
                .email("original@example.com")
                .authUserId(authUserId)
                .firstName("Old")
                .lastName("Name")
                .build();
        existing.setId(1L);

        UserProfileRequest request = UserProfileRequest.builder()
                .email("new-email@example.com")
                .firstName("New")
                .lastName("Surname")
                .headline("Engineer")
                .biography("Bio")
                .avatarUrl("https://img")
                .socialLinks(Map.of("github", "gh"))
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .email("original@example.com")
                .firstName("New")
                .lastName("Surname")
                .build();

        when(userProfileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(existing));
        when(userMapper.toResponseFromEntity(existing)).thenReturn(response);

        UserProfileResponse result = userService.updateMe(authUserId, "original@example.com", request);

        assertEquals("original@example.com", existing.getEmail());
        assertEquals("New", existing.getFirstName());
        assertEquals("Surname", existing.getLastName());
        assertEquals("original@example.com", result.getEmail());
        verify(userProfileRepository).save(existing);
    }

    @Test
    void create_shouldThrowValidationError_whenEmailIsBlank() {
        UserProfileRequest request = UserProfileRequest.builder()
                .email("   ")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(request));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void create_shouldNormalizeEmail_beforeSave() {
        UserProfileRequest request = UserProfileRequest.builder()
                .email("  Test.User@Example.com ")
                .firstName("Test")
                .lastName("User")
                .build();
        UserProfile mappedEntity = UserProfile.builder()
                .firstName("Test")
                .lastName("User")
                .build();
        UserProfileResponse response = UserProfileResponse.builder()
                .email("test.user@example.com")
                .firstName("Test")
                .lastName("User")
                .build();

        when(userProfileRepository.existsByEmailIgnoreCase("test.user@example.com")).thenReturn(false);
        when(userMapper.toEntityFromRequest(request)).thenReturn(mappedEntity);
        when(userMapper.toResponseFromEntity(mappedEntity)).thenReturn(response);

        UserProfileResponse result = userService.create(request);

        assertEquals("test.user@example.com", mappedEntity.getEmail());
        assertEquals("test.user@example.com", result.getEmail());

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertEquals("test.user@example.com", captor.getValue().getEmail());
    }

    @Test
    void create_shouldThrowUserAlreadyExists_whenEmailAlreadyExists() {
        UserProfileRequest request = UserProfileRequest.builder()
                .email("exists@example.com")
                .build();

        when(userProfileRepository.existsByEmailIgnoreCase("exists@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(request));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }
}
