package br.com.betogontijo.sbgqueryprocessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SbgqueryprocessorApplication {

	@Autowired
	WebServiceController webServiceController;

	public static void main(String[] args) {
		SpringApplication.run(SbgqueryprocessorApplication.class, args);
		// ClassPathXmlApplicationContext context = new
		// ClassPathXmlApplicationContext("spring.xml");
		// context.getBean("webService");
		// context.close();
	}
}
