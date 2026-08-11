package org.example.csa_backend.storycontent.migration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("!content-migration")
public class ContentMigrationSchedulingConfig {
}
