package com.infocz.gopid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.infocz.gopid.service.ProjectService;

@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
public class GopidSpringApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(GopidSpringApplication.class, args);
		
        ProjectService projectService = context.getBean(ProjectService.class);

        @SuppressWarnings({ "unchecked", "rawtypes" })
		List<Map<String, Object>> results1 = projectService.projectListForTest1(new HashMap());
        System.out.println(results1);
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
		List<Map<String, Object>> results2 = projectService.projectListForTest2(new HashMap());
        System.out.println(results2);
	}

}
