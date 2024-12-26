package com.infocz.gopid.controller;

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

import com.infocz.gopid.service.DrawingService;

@RestController
@RequestMapping("/api/drawing")
public class DrawingController {

	@Autowired
	private DrawingService drawingService;
	
    @PostMapping("/dependencies")
    public Map <String, List<Map<String, Object>>> listDependencies(@RequestBody HashMap<String, Object> map) {
    	String projectId = (String)map.get("projectId");
    	String drawingId = (String)map.get("drawingId");
    	return drawingService.listDependencies(projectId, drawingId);
    }

    @GetMapping("/run_detail/{drawingId:.+}/{runId:.+}")
    public Map<String, Object> getRunDetail(@PathVariable String drawingId, @PathVariable String runId) {
    	System.out.println("getRunDetail");
    	return drawingService.getDrawingRunGraph(drawingId, runId);
    }
    
    @PostMapping("/run_update")
    public Map<String, Object> saveGraph(@RequestBody HashMap<String, Object> map) {
    	System.out.println("saveGraph");
    	drawingService.saveGraph(map);
        return map;
    }
}
