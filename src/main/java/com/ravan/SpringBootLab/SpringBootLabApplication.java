package com.ravan.SpringBootLab;

import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableKafka
@EnableCaching
@SpringBootApplication
public class SpringBootLabApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootLabApplication.class, args);
	}

}
