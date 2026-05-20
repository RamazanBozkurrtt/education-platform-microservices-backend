package com.edubase.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.account-reactivation")
@Getter
@Setter
public class AccountReactivationProperties {

    private Duration tokenExpiration = Duration.ofHours(1);
}
