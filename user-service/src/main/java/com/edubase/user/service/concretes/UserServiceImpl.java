package com.edubase.user.service.concretes;


import com.edubase.commonCore.events.InstructorEventType;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.configuration.mapper.UserMapper;
import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.entity.UserStatus;
import com.edubase.user.messaging.InstructorLifecycleDomainEvent;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.UserService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String PUBLIC_AVATAR_PATH_TEMPLATE = "/api/v1/users/public/avatar/%d";
    private static final List<String> SUPPORTED_AVATAR_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "svg");
    private static final Set<String> SUPPORTED_AVATAR_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/svg+xml"
    );

    private final UserProfileRepository userProfileRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MinioClient minioClient;

    @Value("${user.media.avatar-base-path:images/avatars}")
    private String avatarBasePath;

    @Value("${user.media.minio.bucket:edubase-media}")
    private String mediaBucket;

    @Value("${user.media.minio.auto-create-bucket:true}")
    private boolean autoCreateBucket;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(cacheNames = "userProfilesPaged", key = "T(String).valueOf(#pageNumber).concat(':').concat(T(String).valueOf(#pageSize))")
    public CustomPageResponse<UserProfileResponse> getAll(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber,pageSize, Sort.by("createdAt"));
        Page<UserProfile> userPage = userProfileRepository.findAll(pageable);

        List<UserProfileResponse> responseList = userMapper.toResponseListFromEntityList(userPage.getContent());

        return CustomPageResponse.of(userPage,responseList);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Cacheable(cacheNames = "userProfilesById", key = "#id")
    public UserProfileResponse getById(Long id) {
        UserProfile dbUser = userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toResponseFromEntity(dbUser);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
            @CacheEvict(cacheNames = "userProfilesById", key = "#id"),
            @CacheEvict(cacheNames = "userProfilesPaged", allEntries = true),
            @CacheEvict(cacheNames = "userProfilesByAuth", allEntries = true)
    })
    public UserProfileResponse update(Long id, UserProfileRequest userProfileRequest) {
        UserProfile existing = userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        applyUpdates(existing, userProfileRequest);
        ensureStatus(existing);
        userProfileRepository.save(existing);
        applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                InstructorEventType.INSTRUCTOR_UPDATED,
                existing
        ));

        return userMapper.toResponseFromEntity(existing);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Cacheable(cacheNames = "userProfilesByAuth", key = "T(String).valueOf(#authUserId).concat(':').concat(#authEmail == null ? '' : #authEmail)")
    public UserProfileResponse getMe(Long authUserId, String authEmail) {
        UserProfile dbUser = resolveOwnProfile(authUserId, authEmail);

        return userMapper.toResponseFromEntity(dbUser);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Caching(evict = {
            @CacheEvict(cacheNames = "userProfilesByAuth", key = "T(String).valueOf(#authUserId).concat(':').concat(#authEmail == null ? '' : #authEmail)"),
            @CacheEvict(cacheNames = "userProfilesById", allEntries = true),
            @CacheEvict(cacheNames = "userProfilesPaged", allEntries = true)
    })
    public UserProfileResponse updateMe(Long authUserId, String authEmail, UserProfileRequest userProfileRequest) {
        UserProfile existing = resolveOwnProfile(authUserId, authEmail);

        applyUpdates(existing, userProfileRequest);
        ensureStatus(existing);
        userProfileRepository.save(existing);
        applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                InstructorEventType.INSTRUCTOR_UPDATED,
                existing
        ));

        return userMapper.toResponseFromEntity(existing);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Caching(evict = {
            @CacheEvict(cacheNames = "userProfilesByAuth", key = "T(String).valueOf(#authUserId).concat(':').concat(#authEmail == null ? '' : #authEmail)"),
            @CacheEvict(cacheNames = "userProfilesById", allEntries = true),
            @CacheEvict(cacheNames = "userProfilesPaged", allEntries = true)
    })
    public UserProfileResponse uploadMyAvatar(Long authUserId, String authEmail, MultipartFile file) {
        validateAvatarFile(file);

        UserProfile profile = resolveOwnProfile(authUserId, authEmail);
        if (profile.getId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String avatarExtension = resolveAvatarExtension(file);
        ensureBucketForWrites();
        removeExistingAvatarObjects(profile.getId(), avatarExtension);
        putAvatarObject(profile.getId(), avatarExtension, file, normalizedAvatarContentType(file, avatarExtension));

        profile.setAvatarUrl(buildPublicAvatarUrl(profile.getId()));
        ensureStatus(profile);
        userProfileRepository.save(profile);
        applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                InstructorEventType.INSTRUCTOR_UPDATED,
                profile
        ));

        return userMapper.toResponseFromEntity(profile);
    }

    @Override
    public ResponseEntity<Resource> getPublicAvatar(Long profileId) {
        if (profileId == null || profileId <= 0) {
            return ResponseEntity.notFound().build();
        }

        for (String extension : SUPPORTED_AVATAR_EXTENSIONS) {
            ResponseEntity<Resource> response = tryBuildAvatarResponse(profileId, extension);
            if (response != null) {
                return response;
            }
        }

        return ResponseEntity.notFound().build();
    }

    private void applyUpdates(UserProfile existing, UserProfileRequest userProfileRequest) {
        existing.setFirstName(userProfileRequest.getFirstName());
        existing.setLastName(userProfileRequest.getLastName());
        existing.setHeadline(userProfileRequest.getHeadline());
        existing.setBiography(userProfileRequest.getBiography());
        existing.setAvatarUrl(userProfileRequest.getAvatarUrl());
        existing.setSocialLinks(userProfileRequest.getSocialLinks());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
            @CacheEvict(cacheNames = "userProfilesById", allEntries = true),
            @CacheEvict(cacheNames = "userProfilesPaged", allEntries = true),
            @CacheEvict(cacheNames = "userProfilesByAuth", allEntries = true)
    })
    public UserProfileResponse create(UserProfileRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (userProfileRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        UserProfile userProfile = userMapper.toEntityFromRequest(request);
        userProfile.setEmail(normalizedEmail);
        if (userProfile.getStatus() == null) {
            userProfile.setStatus(UserStatus.ACTUAL);
        }
        userProfileRepository.save(userProfile);
        applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                InstructorEventType.INSTRUCTOR_CREATED,
                userProfile
        ));
        return userMapper.toResponseFromEntity(userProfile);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String buildPublicAvatarUrl(Long profileId) {
        return PUBLIC_AVATAR_PATH_TEMPLATE.formatted(profileId);
    }

    private ResponseEntity<Resource> tryBuildAvatarResponse(Long profileId, String extension) {
        String objectKey = buildAvatarObjectKey(profileId, extension);
        StatObjectResponse statObjectResponse = statObjectOrNull(objectKey);
        if (statObjectResponse == null) {
            return null;
        }

        try {
            InputStreamResource resource = new InputStreamResource(
                    minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(mediaBucket)
                                    .object(objectKey)
                                    .build()
                    )
            );
            MediaType mediaType = resolveMediaType(statObjectResponse.contentType(), extension);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(statObjectResponse.size())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                    .body(resource);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private MediaType resolveMediaType(String contentType, String extension) {
        String normalized = normalizedContentType(contentType);
        if (!normalized.isBlank()) {
            try {
                return MediaType.parseMediaType(normalized);
            } catch (Exception ignore) {
            }
        }

        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private void ensureBucketForWrites() {
        if (!autoCreateBucket) {
            return;
        }

        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(mediaBucket)
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(mediaBucket)
                                .build()
                );
            }
        } catch (IOException ex) {
            throw storageException(ex);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private void removeExistingAvatarObjects(Long profileId, String keepExtension) {
        for (String extension : SUPPORTED_AVATAR_EXTENSIONS) {
            if (!extension.equals(keepExtension)) {
                removeObjectIfExists(buildAvatarObjectKey(profileId, extension));
            }
        }
    }

    private void putAvatarObject(Long profileId, String extension, MultipartFile file, String contentType) {
        String objectKey = buildAvatarObjectKey(profileId, extension);

        try (var inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private void removeObjectIfExists(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if (isObjectMissing(ex)) {
                return;
            }
            throw storageException(ex);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private StatObjectResponse statObjectOrNull(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if (isObjectMissing(ex)) {
                return null;
            }
            throw storageException(ex);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private String buildAvatarObjectKey(Long profileId, String extension) {
        String basePath = normalizePathPrefix(avatarBasePath);
        String avatarPath = "%d/avatar.%s".formatted(profileId, extension);
        if (basePath.isBlank()) {
            return avatarPath;
        }
        return "%s/%s".formatted(basePath, avatarPath);
    }

    private String normalizePathPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationException();
        }

        String extension = resolveAvatarExtension(file);
        if (extension == null) {
            throw validationException();
        }

        String contentType = normalizedContentType(file.getContentType());
        if (!contentType.isBlank() && !SUPPORTED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw validationException();
        }
    }

    private String resolveAvatarExtension(MultipartFile file) {
        String extensionFromName = extensionOf(file.getOriginalFilename());
        if (SUPPORTED_AVATAR_EXTENSIONS.contains(extensionFromName)) {
            return extensionFromName;
        }

        String contentType = normalizedContentType(file.getContentType());
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> null;
        };
    }

    private String normalizedAvatarContentType(MultipartFile file, String extension) {
        String contentType = normalizedContentType(file.getContentType());
        if (SUPPORTED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            return contentType;
        }

        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            return normalized.substring(0, separator).trim();
        }
        return normalized;
    }

    private boolean isObjectMissing(ErrorResponseException ex) {
        if (ex.errorResponse() == null || ex.errorResponse().code() == null) {
            return false;
        }

        String errorCode = ex.errorResponse().code();
        return "NoSuchKey".equals(errorCode)
                || "NoSuchObject".equals(errorCode)
                || "NoSuchBucket".equals(errorCode);
    }

    private BusinessException validationException() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException storageException(Exception cause) {
        log.error("Avatar storage operation failed", cause);
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private UserProfile resolveOwnProfile(Long authUserId, String authEmail) {
        if (authUserId != null) {
            return userProfileRepository.findByAuthUserId(authUserId)
                    .orElseGet(() -> resolveLegacyProfileByEmail(authUserId, authEmail));
        }
        return resolveLegacyProfileByEmail(null, authEmail);
    }

    private UserProfile resolveLegacyProfileByEmail(Long authUserId, String authEmail) {
        UserProfile profile = userProfileRepository.findByEmailIgnoreCase(authEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (authUserId != null && profile.getAuthUserId() == null) {
            profile.setAuthUserId(authUserId);
            if (profile.getStatus() == null) {
                profile.setStatus(UserStatus.ACTUAL);
            }
            userProfileRepository.save(profile);
        }

        if (profile.getStatus() == null) {
            profile.setStatus(UserStatus.ACTUAL);
            userProfileRepository.save(profile);
        }
        return profile;
    }

    private void ensureStatus(UserProfile profile) {
        if (profile.getStatus() == null) {
            profile.setStatus(UserStatus.ACTUAL);
        }
    }

}
