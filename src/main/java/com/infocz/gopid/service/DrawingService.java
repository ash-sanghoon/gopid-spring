package com.infocz.gopid.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.infocz.util.neo4j.CypherExecutor;

@Service
public class DrawingService {

	@Autowired
	private CypherExecutor cypherExecutor;

    
    public List<Map<String, Object>> getDrawingList(String projectId){
		HashMap paramMap = new HashMap();
		paramMap.put("uuid", projectId);
        try (Session session = cypherExecutor.getDriver().session(SessionConfig.builder().withDatabase(cypherExecutor.getDatabase()).build())) {
        	try (Transaction tx = session.beginTransaction()) {
		        Result resultDrawing = tx.run("MATCH (d:Drawing) <- [] - (n:Project {uuid:$uuid})  RETURN d ORDER BY d.name", paramMap);
	        	List<Map<String, Object>> listNew = new ArrayList<Map<String, Object>>();
		        if(resultDrawing.hasNext()) {
		        	List<Map<String, Object>> list = (List<Map<String, Object>>)(resultDrawing.list(record -> ((Record) record).get("d").asNode().asMap()));
		        	// to modify
		            for(Map<String, Object> drawing : list) {

		            	List<Map<String, Object>> runList = new ArrayList<Map<String, Object>>();
		        		Result resultRun = tx.run(
		        				""" 
							MATCH (drawing:Drawing {uuid: $uuid})-[:HAS_RUN]->(run:Run)
							OPTIONAL MATCH (run)-[:CONTAINS]->(bbox:Bbox)
							OPTIONAL MATCH (bbox)-[rel:CONNECTS_TO]->(otherBbox:Bbox)
							RETURN 
							    run.uuid AS uuid,
							    run.rundate AS runDate, 
							    run.modelName as modelName,
							    COUNT(DISTINCT bbox) AS bboxCount, 
							    COUNT(DISTINCT CASE 
							        WHEN COALESCE(bbox.state, '') IN [''] OR bbox.state IN ['del', 'upd'] 
							        THEN bbox 
							        ELSE NULL 
							    END) AS bboxFoundCount,
							    COUNT(DISTINCT CASE 
							        WHEN bbox.state = 'add' 
							        THEN bbox 
							        ELSE NULL 
							    END) AS bboxChangedCount,
							    COUNT(DISTINCT otherBbox) AS connectsToCount,
							    COUNT(DISTINCT CASE 
							        WHEN COALESCE(rel.state, '') IN [''] OR rel.state IN ['del', 'upd'] 
							        THEN rel 
							        ELSE NULL 
							    END) AS connectFoundCount,
							    COUNT(DISTINCT CASE 
							        WHEN rel.state = 'add' 
							        THEN rel 
							        ELSE NULL 
							    END) AS connectChangedCount
							ORDER BY run.uuid
		    	        		""" , drawing);
		        		runList = resultRun.list(Record::asMap);
		        		
		            	Map<String, Object> drawingMapNew = new HashMap();
		            	drawingMapNew.putAll(drawing);
		            	drawingMapNew.put("completeYn", "Y");
		            	drawingMapNew.put("runs", runList);
		            	listNew.add(drawingMapNew);
		            }
		        }
		        return  listNew;
        	}
        }
    }
	
	public Map <String, List<Map<String, Object>>> listDependencies(String projectId, String drawingId) {

		Map <String, List<Map<String, Object>>> jsonResult = new HashMap<>();
        List<Map<String, Object>> drawings = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        try (Session session = cypherExecutor.getSession()) {
            // 도면 및 fromTo 조회
            String queryDrawings = """
                MATCH (p:Project {uuid: $projectUuid})-[:HAS_DRAWING]->(d:Drawing)
                OPTIONAL MATCH (d)-[:HAS_RUN]->(r:Run)-[:CONTAINS]->(s:Bbox) - [:BELONG_TO] -> (z:Symbol {name:"from_to"})
                RETURN d.uuid AS drawingUuid, d.name AS name, d.width AS width, d.height AS height, 
                       COALESCE(s.id, '') AS fromToId, COALESCE(s.text, '') AS text, 
                       COALESCE(s.top_x , 0) * d.width AS topX, 
                       COALESCE(s.top_y, 0) * d.height  AS topY, 
                       COALESCE(s.bottom_x, 0) * d.width  AS bottomX, 
                       COALESCE(s.bottom_y, 0) * d.height AS bottomY
            """;

            List<Record> records = session.run(queryDrawings, Map.of("projectUuid", projectId)).list();

            Map<String, List<Map<String, Object>>> fromToMap = new HashMap<>();
            Map<String, Map<String, Object>> drawingMap = new HashMap<>();
            for (Record record : records) {
                String drawingUuid = record.get("drawingUuid").asString();
                fromToMap.putIfAbsent(drawingUuid, new ArrayList<>());

                Map<String, Object> fromTo = new HashMap<>();
                String fromToId = record.get("fromToId").asString();
                fromTo.put("id", fromToId);
                fromTo.put("text", record.get("text").asString());
                fromTo.put("top_x", record.get("topX").asDouble());
                fromTo.put("top_y", record.get("topY").asDouble());
                fromTo.put("bottom_x", record.get("bottomX").asDouble());
                fromTo.put("bottom_y", record.get("bottomY").asDouble());
                if(!"".equals(fromToId)) {
                	fromToMap.get(drawingUuid).add(fromTo);
                }
                
                Map<String, Object> drawingContMap = new HashMap<>();
                drawingContMap.put("uuid", drawingUuid);
                drawingContMap.put("name", record.get("name").asString());
                drawingContMap.put("width", record.get("width").asDouble());
                drawingContMap.put("height", record.get("height").asDouble());
                drawingMap.putIfAbsent(drawingUuid, drawingContMap);
            }

            for (String drawingUuid : fromToMap.keySet()) {
            	HashMap mm = (HashMap) drawingMap.get(drawingUuid);
            	mm.put("fromTos", fromToMap.get(drawingUuid));
                drawings.add(mm);
            }

            // 도면 간 연결(edge) 조회
            String queryEdges = """
                MATCH (s1:symbol {symbol_type: "fromTo"})-[:다시....]->(s2:symbol {symbol_type: "fromTo"})
                MATCH (d1:Drawing)-[:HAS_RUN]->(:run)-[:CONTAINS]->(s1)
                MATCH (d2:Drawing)-[:HAS_RUN]->(:run)-[:CONTAINS]->(s2)
                RETURN d1.uuid AS sourceDrawingUuid, s1.id AS sourceFromToId, 
                       d2.uuid AS targetDrawingUuid, s2.id AS targetFromToId
            """;

            List<Record> edgeRecords = session.run(queryEdges).list();
            for (Record record : edgeRecords) {
                edges.add(Map.of(
                        "source", Map.of(
                                "drawingUuid", record.get("sourceDrawingUuid").asString(),
                                "fromToId", record.get("sourceFromToId").asString()
                        ),
                        "target", Map.of(
                                "drawingUuid", record.get("targetDrawingUuid").asString(),
                                "fromToId", record.get("targetFromToId").asString()
                        )
                ));
            }
        }

        jsonResult.put("drawings", drawings);
        jsonResult.put("edges", edges);

        return jsonResult;
	}
	
	public Map<String, Object> getDrawingRunGraph(String drawingId, String runId) {
        Map<String, Object> jsonData = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        jsonData.put("nodes", nodes);
        jsonData.put("edges", edges);

        if("0".equals(runId)) {
            try (Session session = cypherExecutor.getSession()) {
            	String drawingQuery = """
                    MATCH (d:Drawing {uuid:$uuid}) 
                    RETURN d.name AS name, d.width AS width, d.height AS height, d.uuid as drawingUuid
                        """;

	            Map<String, Object> drawingParamData = new HashMap<>();
	            drawingParamData.put("uuid", drawingId);
                Result modelResult = session.run(drawingQuery, drawingParamData);
    	        if(!modelResult.hasNext()) { throw new RuntimeException("No drawing");}
            	jsonData.put("drawing", modelResult.next().asMap());
            }
        	return jsonData;
        }
        
        Map<String, Object> parmData = new HashMap<>();
        parmData.put("uuid", runId);
        try (Session session = cypherExecutor.getSession()) {
        	
            String runQuery = """
                MATCH (d:Drawing) - [:HAS_RUN] -> (r:Run  {uuid:$uuid})
                RETURN d.name AS name, d.width AS width, d.height AS height, r.uuid AS runUuid, d.uuid as drawingUuid
                    """;

            Result runResult = session.run(runQuery, parmData);
	        if(!runResult.hasNext()) { throw new RuntimeException("No drawing");}
        	jsonData.put("drawing", runResult.next().asMap());
        	
            // Fetch nodes
            String fetchNodesQuery = """
            	MATCH (d:Drawing) - [:HAS_RUN] -> (r:Run  {uuid:$uuid})
                MATCH (r)-[:CONTAINS]->(n:Bbox) - [:BELONG_TO] -> (s:Symbol)
                WHERE COALESCE(n.state, '') <> 'del'
                RETURN n.id AS id, 
                       s.name AS symbol_type, 
                       n.top_x * d.width AS top_x, 
                       n.top_y * d.height AS top_y, 
                       n.bottom_x * d.width AS bottom_x, 
                       n.bottom_y * d.height AS bottom_y,
                       n.text AS text,
                       n.line_no AS line_no,
                       n.state AS state
                """;

            Result nodeResult = session.run(fetchNodesQuery, parmData);
            while (nodeResult.hasNext()) {
                Record record = nodeResult.next();
                Map<String, Object> node = new HashMap<>();
                node.put("name", record.get("id").asString());
                node.put("properties", Map.of("label", record.get("symbol_type").asString(),
                		"text", record.get("text").asString(),
                		"line_no", record.get("line_no").asString()
                		));
                node.put("position", List.of(
                        List.of(record.get("top_x").asDouble(), record.get("top_y").asDouble()),
                        List.of(record.get("bottom_x").asDouble(), record.get("bottom_y").asDouble())
                ));
                nodes.add(node);
            }

            // Fetch edges
            String fetchEdgesQuery = """
            	MATCH (run:Run {uuid:$uuid})-[:CONTAINS]->(bbox:Bbox)
                MATCH (bbox)-[r:CONNECTS_TO]->(target:Bbox)
                WHERE COALESCE(r.state, '') <> 'del'
                RETURN r.id AS id, 
                       bbox.id AS source, 
                       target.id AS target
                """;

            Result edgeResult = session.run(fetchEdgesQuery, parmData);
            while (edgeResult.hasNext()) {
                Record record = edgeResult.next();
                Map<String, Object> edge = new HashMap<>();
                edge.put("name", record.get("id").asString());
                edge.put("source", record.get("source").asString());
                edge.put("target", record.get("target").asString());
                edges.add(edge);
            }
        }

        return jsonData;
    }
    
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> saveGraph(HashMap<String, Object> map) {
		Map<String, Object> pushJson = (Map<String, Object>) (map.get("push"));
		String action = (String)(pushJson.get("action"));
		String targetName = (String)(pushJson.get("name"));
		String targetKind = (String)(pushJson.get("kind"));
		
		Map<String, List<Map<String,Object>>> originJson = (Map<String, List<Map<String,Object>>>) (map.get("origin"));
        String runId = (String) ((Map<String, Object>) originJson.get("drawing")).get("runUuid");
		if("node".equals(targetKind)) {
	        Map<String, Object> selectedNode = originJson.get("nodes").stream()
            .filter(node -> targetName.equals(node.get("name")))
            .toList().get(0);
	        updateNode(runId, selectedNode, action);
		}else {
	        Map<String, Object> selectedNode = originJson.get("edges").stream()
	                .filter(node -> targetName.equals(node.get("name")))
	                .toList().get(0);
	    	updateEdge(runId, selectedNode, action);
		}
		
		
		return null;
	}
	private void updateEdge(String runId, Map<String, Object> edge, String action) {
        try (Session session = cypherExecutor.getSession()) {
        	try (Transaction tx = session.beginTransaction()) {
	            Map<String, Object> properties = (Map<String, Object>) edge.get("properties");
	        	//if(!"Y".equals(properties.get("last_update"))) continue;
	            String edge_name = (String) edge.get("name");
	            String source = (String) edge.get("source");
	            String target = (String) edge.get("target");
	            String line_no = (String) properties.get("line_no");
	            
	        	Result result = tx.run("""
					MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:Bbox {id: $sourceId}) - [p:CONNECTS_TO]->(t:Bbox {id: $targetId}) <- [:CONTAINS] - (r)
	        		RETURN COALESCE(p.state, '') AS state
                    """, 
                    Map.of(
	                    "uuid", runId,
                        "sourceId", source,
                        "targetId", target
                    )
	            );
	        	String beforeState = "";
	        	if(result.hasNext()) {
	        		beforeState = (String)(result.next().asMap().get("state"));
	        	}
	        	
	        	if("add".equals(beforeState) && "del".equals(action)) {
		            result = tx.run("""
							MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:Bbox {id: $sourceId}) - [p:CONNECTS_TO]->(t:Bbox {id: $targetId}) <- [:CONTAINS] - (r)
			        		DELETE p
		                    """, 
		                    Map.of(
			                    "uuid", runId,
		                        "sourceId", source,
		                        "targetId", target
		                    )
	            		);
	        	}else{
	        		String tobeState = action;
	        		if("add".equals(beforeState)) {
	        			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
	        		}
		            result = tx.run("""
			            	MATCH (run:Run  {uuid:$uuid})
							MATCH (run)-[:CONTAINS]->(source:Bbox {id: $sourceId})
							MATCH (run)-[:CONTAINS]->(target:Bbox {id: $targetId})
		                    MERGE (source)-[r:CONNECTS_TO]->(target)
		                    ON CREATE SET r.id = $edgeId,
		            					  r.line_no = $line_no,
		            					  r.state = $state
		            		ON MATCH SET r.id = $edgeId,
		            					  r.line_no = $line_no,
		            					  r.state = $state
	                    """,
	                    Map.of(
		                    "uuid", runId,
	                        "sourceId", source,
	                        "targetId", target,
	                        "edgeId", edge_name,
	                        "line_no", line_no,
	                        "state", tobeState
	                    )
	                );
	        	}

			    // 통계 정보 확인
			    SummaryCounters counters = result.consume().counters();
			    System.out.println("Nodes created: " + counters.nodesCreated());
			    System.out.println("Relationships created: " + counters.relationshipsCreated());
			    System.out.println("Properties set: " + counters.propertiesSet());
			    
			    tx.commit();
        	}
        }
	}
	
	private void updateNode(String runId, Map<String, Object> node, String action) {
        try (Session session = cypherExecutor.getSession()) {
        	try (Transaction tx = session.beginTransaction()) {
	            String nodeName = (String) node.get("name");
	            Map<String, Object> properties = (Map<String, Object>) node.get("properties");
	        	//if(!"Y".equals(properties.get("last_update"))) continue;
		    	Result result = tx.run("""
					MATCH (run:Run {uuid:$uuid})-[:CONTAINS]->(s:Bbox {id: $id}) RETURN COALESCE(s.state, '') AS state
		            """, 
		            Map.of(
		                "uuid", runId,
		                "id", nodeName
		            )
		        );
		    	String beforeState = "";
		    	if(result.hasNext()) {
		    		beforeState = (String)(result.next().asMap().get("state"));
		    	}
		    	
		    	if("add".equals(beforeState) && "del".equals(action)) {
		            result = tx.run("""
		            	MATCH (run:Run  {uuid:$uuid}) - [:CONTAINS] -> (s:Bbox {id:$id})
						DETACH DELETE s
		        		""", 
		        		Map.of(
		        				"uuid", runId,
		        				"id", nodeName
		        				)
		        		);
		    	}else{
		    		String tobeState = action;
		    		if("add".equals(beforeState)) {
		    			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
		    		}
		            List<List<Double>> position = (List<List<Double>>) node.get("position");
		
		            // Run 노드와 Symbol 노드를 찾거나 생성하고, 관계를 설정하는 쿼리
		            result = tx.run("""
		            	MATCH (d:Drawing) - [:HAS_RUN] -> (run:Run  {uuid:$uuid})
						MERGE (run)-[:CONTAINS]->(s:Bbox {id: $id})
						ON CREATE SET s.top_x = toFloat($topX) / d.width, 
		            		          s.top_y = toFloat($topY) / d.height, 
		            		          s.bottom_x = toFloat($bottomX) / d.width, 
		            		          s.bottom_y = toFloat($bottomY) / d.height, 
		            		          s.text = $text,
		            		          s.line_no = $line_no,
		            		          s.state = $state
						ON MATCH SET  s.top_x = toFloat($topX) / d.width, 
		            		          s.top_y = toFloat($topY) / d.height, 
		            		          s.bottom_x = toFloat($bottomX) / d.width, 
		            		          s.bottom_y = toFloat($bottomY) / d.height, 
		            		          s.text = $text,
		            		          s.line_no = $line_no,
		            		          s.state = $state
		                """, 
		                Map.of(
		                    "uuid", runId,
		                    "id", nodeName,
		                    "text", Objects.requireNonNullElse(properties.get("text"), ""),
		                    "line_no", Objects.requireNonNullElse(properties.get("line_no"), ""),
		                    "topX", position.get(0).get(0),
		                    "topY", position.get(0).get(1),
		                    "bottomX", position.get(1).get(0),
		                    "bottomY", position.get(1).get(1),
		                    "state", Objects.requireNonNullElse(tobeState, "")
		                )
		             );
		            result = tx.run("""
						MATCH (d:Drawing)-[:HAS_RUN]->(run:Run {uuid: $uuid})
						MERGE (run)-[:CONTAINS]->(b:Bbox {id: $id})
						WITH b
						MATCH (b)-[r:BELONG_TO]->(s:Symbol)
						DELETE r
						WITH b
						MATCH (s2:Symbol {name: $label})
						MERGE (b)-[:BELONG_TO]->(s2)
		                """, 
		                Map.of(
		                    "uuid", runId,
		                    "id", nodeName,
		                    "label", properties.get("label")
		                )
		             );
		            
		            if("del".equals(properties.get("state"))) { // edge에도 del state 설정
			            result = tx.run("""
								MATCH (s:Bbox {id: $id}) - [p:CONNECTS_TO {line_no:$line_no}]->(:Bbox)
								SET   p.state = 'del'
			                    """, 
			                    Map.of("id", nodeName,
					                   "line_no", Objects.requireNonNullElse(properties.get("text"), "")
					            )
		            		);
		            }
		    	}
		
			    // 통계 정보 확인
			    SummaryCounters counters = result.consume().counters();
			    System.out.println("Nodes created: " + counters.nodesCreated());
			    System.out.println("Relationships created: " + counters.relationshipsCreated());
			    System.out.println("Properties set: " + counters.propertiesSet());
			    
			    tx.commit();
        	}
        }
	}
//	
//	@SuppressWarnings("unchecked")
//	public List<Map<String, Object>> saveGraph1(HashMap<String, Object> map) {
//        try (Session session = cypherExecutor.getSession()) {
//        	try (Transaction tx = session.beginTransaction()) {
//		        // Nodes 데이터 처리
//	            String runId = (String) ((Map<String, Object>) map.get("drawing")).get("runUuid");
//		        
//		        for (Map<String, Object> node : (List<Map<String, Object>>) map.get("nodes")) {
//		            String nodeName = (String) node.get("name");
//		            Map<String, Object> properties = (Map<String, Object>) node.get("properties");
//		        	//if(!"Y".equals(properties.get("last_update"))) continue;
//		        	
//		        	Result result = tx.run("""
//						MATCH (run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $id}) RETURN COALESCE(s.state, '') AS state
//	                    """, 
//	                    Map.of(
//		                    "uuid", runId,
//	                        "id", nodeName
//	                    )
//		            );
//		        	String beforeState = "";
//		        	if(result.hasNext()) {
//		        		beforeState = (String)(result.next().asMap().get("state"));
//		        	}
//		        	
//		        	if("add".equals(beforeState) && "del".equals(properties.get("state"))) {
//			            result = tx.run("""
//			            	MATCH (run:run  {uuid:$uuid}) - [:CONTAINS] -> (s:symbol {id:$id})
//							DETACH DELETE s
//		            		""", 
//		            		Map.of(
//		            				"uuid", runId,
//		            				"id", nodeName
//		            				)
//		            		);
//		        	}else{
//		        		String tobeState = (String)properties.get("state");
//		        		if("add".equals(beforeState)) {
//		        			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
//		        		}
//			            List<List<Double>> position = (List<List<Double>>) node.get("position");
//			
//			            // Run 노드와 Symbol 노드를 찾거나 생성하고, 관계를 설정하는 쿼리
//			            result = tx.run("""
//			            	MATCH (d:Drawing) - [:HAS_RUN] -> (run:run  {uuid:$uuid})
//							MERGE (run)-[:CONTAINS]->(s:symbol {id: $id})
//							ON CREATE SET s.symbol_type = $symbolType,
//			            		          s.top_x = toFloat($topX) / d.width, 
//			            		          s.top_y = toFloat($topY) / d.height, 
//			            		          s.bottom_x = toFloat($bottomX) / d.width, 
//			            		          s.bottom_y = toFloat($bottomY) / d.height, 
//			            		          s.state = $state
//							ON MATCH SET  s.symbol_type = $symbolType,
//			            		          s.top_x = toFloat($topX) / d.width, 
//			            		          s.top_y = toFloat($topY) / d.height, 
//			            		          s.bottom_x = toFloat($bottomX) / d.width, 
//			            		          s.bottom_y = toFloat($bottomY) / d.height, 
//			            		          s.state = $state
//		                    """, 
//		                    Map.of(
//			                    "uuid", runId,
//		                        "id", nodeName,
//		                        "symbolType", properties.get("label"),
//		                        "topX", position.get(0).get(0),
//		                        "topY", position.get(0).get(1),
//		                        "bottomX", position.get(1).get(0),
//		                        "bottomY", position.get(1).get(1),
//		                        //"state", tobeState
//		                        "state", ""
//		                    )
//			             );
//		        	}
//
//				    // 통계 정보 확인
//				    SummaryCounters counters = result.consume().counters();
//				    System.out.println("Nodes created: " + counters.nodesCreated());
//				    System.out.println("Relationships created: " + counters.relationshipsCreated());
//				    System.out.println("Properties set: " + counters.propertiesSet());
//		        }
//
//		     
//		        // Edges 데이터 처리
//		        for (Map<String, Object> edge : (List<Map<String, Object>>) map.get("edges")) {
//		            Map<String, Object> properties = (Map<String, Object>) edge.get("properties");
//		        	//if(!"Y".equals(properties.get("last_update"))) continue;
//		            String edgeName = (String) edge.get("name");
//		            String source = (String) edge.get("source");
//		            String target = (String) edge.get("target");
//		            
//		        	Result result = tx.run("""
//						MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $sourceId}) - [p:PIPED_TO]->(t:symbol {id: $targetId}) <- [:CONTAINS] - (r)
//		        		RETURN COALESCE(p.state, '') AS state
//	                    """, 
//	                    Map.of(
//		                    "uuid", runId,
//	                        "sourceId", source,
//	                        "targetId", target
//	                    )
//		            );
//		        	String beforeState = "";
//		        	if(result.hasNext()) {
//		        		beforeState = (String)(result.next().asMap().get("state"));
//		        	}
//		        	
//		        	if("add".equals(beforeState) && "del".equals(properties.get("state"))) {
//			            result = tx.run("""
//								MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $sourceId}) - [p:PIPED_TO]->(t:symbol {id: $targetId}) <- [:CONTAINS] - (r)
//				        		DELETE p
//			                    """, 
//			                    Map.of(
//				                    "uuid", runId,
//			                        "sourceId", source,
//			                        "targetId", target
//			                    )
//		            		);
//		        	}else{
//		        		String tobeState = (String)properties.get("state");
//		        		if("add".equals(beforeState)) {
//		        			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
//		        		}
//			            result = tx.run("""
//				            	MATCH (run:run  {uuid:$uuid})
//								MATCH (run)-[:CONTAINS]->(source:symbol {id: $sourceId})
//								MATCH (run)-[:CONTAINS]->(target:symbol {id: $targetId})
//			                    MERGE (source)-[r:PIPED_TO]->(target)
//			                    ON CREATE SET r.id = $edgeId,
//			            					  r.state = $state
//			            		ON MATCH SET r.id = $edgeId,
//			            					 r.state = $state
//		                    """,
//		                    Map.of(
//			                    "uuid", runId,
//		                        "sourceId", source,
//		                        "targetId", target,
//		                        "edgeId", edgeName,
//		                        //"state", tobeState
//		                        "state", ""
//		                    )
//		                );
//		        	}
//
//				    // 통계 정보 확인
//				    SummaryCounters counters = result.consume().counters();
//				    System.out.println("Nodes created: " + counters.nodesCreated());
//				    System.out.println("Relationships created: " + counters.relationshipsCreated());
//				    System.out.println("Properties set: " + counters.propertiesSet());
//		        }
//		        tx.commit();
//        	}
//        }
//		return null;
//	}
}