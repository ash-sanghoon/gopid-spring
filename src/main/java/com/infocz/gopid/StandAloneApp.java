package com.infocz.gopid;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.infocz.util.ocr.ZDEL_BboxTaggingService;

@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
public class StandAloneApp {
    public static void main(String[] args) throws IOException {
        SpringApplication app = new SpringApplication(StandAloneApp.class);
        
        // 환경변수나 시스템 프로퍼티로 모드 결정
        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
        if ("standalone".equals(mode)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        ZDEL_BboxTaggingService bboxTaggingService = context.getBean(ZDEL_BboxTaggingService.class);
        
        bboxTaggingService.tagBbox("");
    }
}
