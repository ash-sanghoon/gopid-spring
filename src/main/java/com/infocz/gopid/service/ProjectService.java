package com.infocz.gopid.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.infocz.util.neo4j.CypherExecutor;

@Service
public class ProjectService {
	
	@Autowired
	private CypherExecutor neo4jConnectionComponent;

    public List<Map<String, Object>> projectList(HashMap<String, Object> reqMap) {
    	
        String query = "MATCH (n:Project) RETURN n LIMIT 25";
//        		"MATCH (p:Project)   "+
//        		"WHERE p.name CONTAINS $projectName  "+  
//        		"WITH collect(p) AS projectNodes   "+
//        		"MATCH (c:Client)   "+
//        		"WHERE c.name CONTAINS $clientName    "+ 
//        		"WITH projectNodes, c   "+
//        		"MATCH (c)-[:HAS_DRAWING]->(d:Drawing)   "+ 
//        		"WITH projectNodes, d, c     "+
//        		"MATCH (d)<-[:HAS_DRAWING]-(p:Project)     "+ 
//        		"WHERE p IN projectNodes        "+
//        		"WITH p, c, d                   "+
//        		"MATCH (c)-[:HAS_STANDARD]->(s:STANDARD)    "+ 
//        		"RETURN DISTINCT p.id AS projectId, p.name AS projectName, c.name AS clientName, d.id AS drawingId,   "+ 
//        		"       d.name AS drawingName, s.name AS standardName ";

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("projectName", reqMap.get("projectName"));
        map.put("clientName", reqMap.get("clientName"));
        System.out.println(query); 
        return neo4jConnectionComponent.readCyphers(query, map, "samsung-ena");
    }
}