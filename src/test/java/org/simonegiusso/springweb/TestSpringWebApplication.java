package org.simonegiusso.springweb;

import org.springframework.boot.SpringApplication;

public class TestSpringWebApplication {

    public static void main(String[] args) {
        SpringApplication.from(SpringWebApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
    }
}
