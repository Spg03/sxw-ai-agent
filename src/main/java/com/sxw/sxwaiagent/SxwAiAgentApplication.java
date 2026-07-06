package com.sxw.sxwaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SxwAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SxwAiAgentApplication.class, args);
    }
}
