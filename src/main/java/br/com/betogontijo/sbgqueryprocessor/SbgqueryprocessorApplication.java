package br.com.betogontijo.sbgqueryprocessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SbgqueryprocessorApplication {

	@Autowired
	WebServicesController controller;
	
	public static void main(String[] args) {
		SpringApplication.run(SbgqueryprocessorApplication.class, args);
	}
}
