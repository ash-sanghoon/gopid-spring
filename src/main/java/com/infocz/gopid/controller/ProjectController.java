package com.infocz.gopid.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.infocz.gopid.service.ProjectService;

@RestController
@RequestMapping("/api/project")
public class ProjectController {

	@Autowired
	private ProjectService projectService;
	
    @PostMapping("/list")
    public List<Map<String, Object>> list(@RequestBody HashMap<String, Object> map) {
    	System.out.println("list");
    	List <Map <String, Object>> projectInfo = projectService.projectList(map);
        return projectInfo;
    }
    
    @GetMapping("/detail/{drawingId:.+}")
    public Map<String, Object> detail(@PathVariable String drawingId) {
    	System.out.println("list");
    	return projectService.projectDetail(drawingId);
    }
	
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody HashMap<String, Object> map) {
    	System.out.println("create");
    	return projectService.projectCreate(map);
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody HashMap<String, Object> map) throws IOException {
    	System.out.println("save");
    	return projectService.projectSave(map);
    }
}
