package com.zjgsu.zcl.lost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class LostServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LostServiceApplication.class, args);
	}

}
