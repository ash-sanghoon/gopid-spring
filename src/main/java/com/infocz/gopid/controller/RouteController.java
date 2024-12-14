package com.infocz.gopid.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.infocz.gopid.service.ProjectService;

@RestController
public class RouteController {

	@Autowired
	private ProjectService projectService;
	
    @GetMapping("/project/list")
    public List <Map <String, Object>> publish(@RequestBody HashMap<String, Object> map) {
    	List <Map <String, Object>> projectInfo = projectService.projectList(map);
        return projectInfo;
    }
    
    @GetMapping("/api/hello")
    public String getGreeting() {
		return "Hello, Client!";
    }
}