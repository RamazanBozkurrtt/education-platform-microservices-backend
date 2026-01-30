package com.edubase.user.messaging;

import com.edubase.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncConsumer {

    private UserProfileRepository userProfileRepository;




}
