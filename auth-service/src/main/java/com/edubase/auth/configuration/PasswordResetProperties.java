package com.edubase.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.password-reset")
@Getter
@Setter
public class PasswordResetProperties {

    private Duration tokenExpiration = Duration.ofMinutes(15);
}
