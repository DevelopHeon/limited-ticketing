package com.example.limitedticketing.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class ExecutionRoleConfiguration {
    public ExecutionRoleConfiguration(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("api")) && environment.acceptsProfiles(Profiles.of("worker"))) {
            throw new IllegalStateException("api and worker profiles must run in separate processes");
        }
    }
}
