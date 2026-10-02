package com.quoocscuongwf.nextchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class NextchatApplication {

	public static void main(String[] args) {
		SpringApplication.run(NextchatApplication.class, args);
	}

}
