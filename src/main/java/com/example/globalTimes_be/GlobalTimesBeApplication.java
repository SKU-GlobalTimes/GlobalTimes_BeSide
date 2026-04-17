package com.example.globalTimes_be;

import com.example.globalTimes_be.bootstrap.DotenvBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlobalTimesBeApplication {

	public static void main(String[] args) {
		DotenvBootstrap.apply();
		SpringApplication.run(GlobalTimesBeApplication.class, args);
	}

}
