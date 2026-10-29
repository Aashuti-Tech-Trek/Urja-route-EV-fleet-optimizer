package com.moveinsync.evfleet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class EvFleetApplicationTests {

    @Test
    @DisplayName("Spring Boot context loads successfully")
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
