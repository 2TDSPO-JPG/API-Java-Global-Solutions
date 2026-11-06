package br.com.saneia.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Relógio injetável: as regras de 20 dias e de comunicação ficam testáveis. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
