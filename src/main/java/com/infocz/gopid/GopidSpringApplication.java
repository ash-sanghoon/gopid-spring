package com.infocz.gopid;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.infocz.gopid.service.ProjectService;

@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
public class GopidSpringApplication {

	public static void main(String[] args) throws IOException {
		ConfigurableApplicationContext context = SpringApplication.run(GopidSpringApplication.class, args);
        ProjectService projectService = context.getBean(ProjectService.class);
        projectService.toString();
        
	}
}
