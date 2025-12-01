package com.cheatsheet.distributedlock;

import org.springframework.boot.SpringApplication;

public class TestDistributedLockApplication {

    public static void main(String[] args) {
        SpringApplication.from(DistributedLockApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
