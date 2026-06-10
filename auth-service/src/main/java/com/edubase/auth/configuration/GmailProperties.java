package com.edubase.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.gmail")
@Getter
@Setter
public class GmailProperties {

    private boolean enabled = true;
    private String keysFile = "keys.json";
    private String username;
    private String password;
}
