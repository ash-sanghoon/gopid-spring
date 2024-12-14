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

        // 쿼리 실행
        List<Map<String, Object>> results = projectService.projectList(new HashMap());

        // 결과 출력
        for (Map<String, Object> result : results) {
            System.out.println("Project ID: " + result.get("projectId"));
            System.out.println("Project Name: " + result.get("projectName"));
            System.out.println("Client Name: " + result.get("clientName"));
            System.out.println("Drawing ID: " + result.get("drawingId"));
            System.out.println("Drawing Name: " + result.get("drawingName"));
            System.out.println("Standard Name: " + result.get("standardName"));
            System.out.println("---");
        }
	}

}
