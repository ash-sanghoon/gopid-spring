package com.infocz.gopid.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
							MATCH (drawing:Drawing {uuid: $uuid})-[:HAS_RUN]->(run:run)
							OPTIONAL MATCH (run)-[:CONTAINS]->(symbol:symbol)
							OPTIONAL MATCH (symbol)-[rel:PIPED_TO]->(otherSymbol:symbol)
							RETURN 
							    run.uuid AS uuid,
							    run.rundate AS runDate, 
							    run.modelName as modelName,
							    COUNT(DISTINCT symbol) AS symbolCount, 
							    COUNT(DISTINCT CASE 
							        WHEN COALESCE(symbol.state, '') IN [''] OR symbol.state IN ['del', 'upd'] 
							        THEN symbol 
							        ELSE NULL 
							    END) AS symbolFoundCount,
							    COUNT(DISTINCT CASE 
							        WHEN symbol.state = 'add' 
							        THEN symbol 
							        ELSE NULL 
							    END) AS symbolChangedCount,
							    COUNT(DISTINCT otherSymbol) AS pipedToCount,
							    COUNT(DISTINCT CASE 
							        WHEN COALESCE(rel.state, '') IN [''] OR rel.state IN ['del', 'upd'] 
							        THEN rel 
							        ELSE NULL 
							    END) AS pipedFoundCount,
							    COUNT(DISTINCT CASE 
							        WHEN rel.state = 'add' 
							        THEN rel 
							        ELSE NULL 
							    END) AS pipedChangedCount
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
                OPTIONAL MATCH (d)-[:HAS_RUN]->(r:run)-[:CONTAINS]->(s:symbol {symbol_type: "fromTo"})
                RETURN d.uuid AS drawingUuid, d.name AS name, d.width AS width, d.height AS height, 
                       COALESCE(s.id, '') AS fromToId, COALESCE(s.comments, '') AS comments, 
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
                fromTo.put("comments", record.get("comments").asString());
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
                MATCH (s1:symbol {symbol_type: "fromTo"})-[:piped_to]->(s2:symbol {symbol_type: "fromTo"})
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
                MATCH (d:Drawing) - [:HAS_RUN] -> (r:run  {uuid:$uuid})
                RETURN d.name AS name, d.width AS width, d.height AS height, r.uuid AS runUuid, d.uuid as drawingUuid
                    """;

            Result runResult = session.run(runQuery, parmData);
	        if(!runResult.hasNext()) { throw new RuntimeException("No drawing");}
        	jsonData.put("drawing", runResult.next().asMap());
        	
            // Fetch nodes
            String fetchNodesQuery = """
            	MATCH (d:Drawing) - [:HAS_RUN] -> (r:run  {uuid:$uuid})
                MATCH (r)-[:CONTAINS]->(n:symbol)
                WHERE COALESCE(n.state, '') <> 'del'
                RETURN n.id AS id, 
                       n.symbol_type AS symbol_type, 
                       n.top_x * d.width AS top_x, 
                       n.top_y * d.height AS top_y, 
                       n.bottom_x * d.width AS bottom_x, 
                       n.bottom_y * d.height AS bottom_y,
                       n.state AS state
                """;

            Result nodeResult = session.run(fetchNodesQuery, parmData);
            while (nodeResult.hasNext()) {
                Record record = nodeResult.next();
                Map<String, Object> node = new HashMap<>();
                node.put("name", record.get("id").asString());
                node.put("properties", Map.of("label", record.get("symbol_type").asString()));
                node.put("position", List.of(
                        List.of(record.get("top_x").asDouble(), record.get("top_y").asDouble()),
                        List.of(record.get("bottom_x").asDouble(), record.get("bottom_y").asDouble())
                ));
                nodes.add(node);
            }

            // Fetch edges
            String fetchEdgesQuery = """
            	MATCH (run:run {uuid:$uuid})-[:CONTAINS]->(symbol:symbol)
                MATCH (symbol:symbol)-[r:PIPED_TO]->(target:symbol)
                WHERE COALESCE(r.state, '') <> 'del'
                RETURN r.id AS id, 
                       symbol.id AS source, 
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
    public static List<Map<String, Object>> findMaxTimestampRecords(List<Map<String, Object>> records) {
        // reqTimeStamp가 존재하는 레코드 중 가장 큰 값을 찾음
        Optional<Long> maxTimestamp = records.stream()
                .filter(record -> record.containsKey("reqTimeStamp"))
                .map(record -> (Long) record.get("reqTimeStamp"))
                .max(Long::compare);

        // 가장 큰 값이 있는 경우 해당 값을 가진 레코드들을 반환
        return maxTimestamp.map(maxValue -> records.stream()
                .filter(record -> maxValue.equals(record.get("reqTimeStamp")))
                .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
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
	        updateNode(runId, selectedNode);
		}else {
	        Map<String, Object> selectedNode = originJson.get("edges").stream()
	                .filter(node -> targetName.equals(node.get("name")))
	                .toList().get(0);
	    	updateEdge(runId, selectedNode);
		}
		
		
		return null;
	}
	private void updateEdge(String runId, Map<String, Object> edge) {
        try (Session session = cypherExecutor.getSession()) {
        	try (Transaction tx = session.beginTransaction()) {
	            Map<String, Object> properties = (Map<String, Object>) edge.get("properties");
	        	//if(!"Y".equals(properties.get("last_update"))) continue;
	            String edgeName = (String) edge.get("name");
	            String source = (String) edge.get("source");
	            String target = (String) edge.get("target");
	            
	        	Result result = tx.run("""
					MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $sourceId}) - [p:PIPED_TO]->(t:symbol {id: $targetId}) <- [:CONTAINS] - (r)
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
	        	
	        	if("add".equals(beforeState) && "del".equals(properties.get("state"))) {
		            result = tx.run("""
							MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $sourceId}) - [p:PIPED_TO]->(t:symbol {id: $targetId}) <- [:CONTAINS] - (r)
			        		DELETE p
		                    """, 
		                    Map.of(
			                    "uuid", runId,
		                        "sourceId", source,
		                        "targetId", target
		                    )
	            		);
	        	}else{
	        		String tobeState = (String)properties.get("state");
	        		if("add".equals(beforeState)) {
	        			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
	        		}
		            result = tx.run("""
			            	MATCH (run:run  {uuid:$uuid})
							MATCH (run)-[:CONTAINS]->(source:symbol {id: $sourceId})
							MATCH (run)-[:CONTAINS]->(target:symbol {id: $targetId})
		                    MERGE (source)-[r:PIPED_TO]->(target)
		                    ON CREATE SET r.id = $edgeId,
		            					  r.state = $state
		            		ON MATCH SET r.id = $edgeId,
		            					 r.state = $state
	                    """,
	                    Map.of(
		                    "uuid", runId,
	                        "sourceId", source,
	                        "targetId", target,
	                        "edgeId", edgeName,
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
	
	private void updateNode(String runId, Map<String, Object> node) {
        try (Session session = cypherExecutor.getSession()) {
        	try (Transaction tx = session.beginTransaction()) {
	            String nodeName = (String) node.get("name");
	            Map<String, Object> properties = (Map<String, Object>) node.get("properties");
	        	//if(!"Y".equals(properties.get("last_update"))) continue;
		    	Result result = tx.run("""
					MATCH (run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $id}) RETURN COALESCE(s.state, '') AS state
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
		    	
		    	if("add".equals(beforeState) && "del".equals(properties.get("state"))) {
		            result = tx.run("""
		            	MATCH (run:run  {uuid:$uuid}) - [:CONTAINS] -> (s:symbol {id:$id})
						DETACH DELETE s
		        		""", 
		        		Map.of(
		        				"uuid", runId,
		        				"id", nodeName
		        				)
		        		);
		    	}else{
		    		String tobeState = (String)properties.get("state");
		    		if("add".equals(beforeState)) {
		    			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
		    		}
		            List<List<Double>> position = (List<List<Double>>) node.get("position");
		
		            // Run 노드와 Symbol 노드를 찾거나 생성하고, 관계를 설정하는 쿼리
		            result = tx.run("""
		            	MATCH (d:Drawing) - [:HAS_RUN] -> (run:run  {uuid:$uuid})
						MERGE (run)-[:CONTAINS]->(s:symbol {id: $id})
						ON CREATE SET s.symbol_type = $symbolType,
		            		          s.top_x = toFloat($topX) / d.width, 
		            		          s.top_y = toFloat($topY) / d.height, 
		            		          s.bottom_x = toFloat($bottomX) / d.width, 
		            		          s.bottom_y = toFloat($bottomY) / d.height, 
		            		          s.state = $state
						ON MATCH SET  s.symbol_type = $symbolType,
		            		          s.top_x = toFloat($topX) / d.width, 
		            		          s.top_y = toFloat($topY) / d.height, 
		            		          s.bottom_x = toFloat($bottomX) / d.width, 
		            		          s.bottom_y = toFloat($bottomY) / d.height, 
		            		          s.state = $state
		                """, 
		                Map.of(
		                    "uuid", runId,
		                    "id", nodeName,
		                    "symbolType", properties.get("label"),
		                    "topX", position.get(0).get(0),
		                    "topY", position.get(0).get(1),
		                    "bottomX", position.get(1).get(0),
		                    "bottomY", position.get(1).get(1),
		                    "state", tobeState
		                )
		             );
		            
		            if("del".equals(properties.get("state"))) { // edge에도 del state 설정
			            result = tx.run("""
								MATCH (s:symbol {id: $id}) - [p:PIPED_TO]->(:symbol)
								SET   p.state = 'del'
			                    """, 
			                    Map.of("id", nodeName)
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
	
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> saveGraph1(HashMap<String, Object> map) {
        try (Session session = cypherExecutor.getSession()) {
        	try (Transaction tx = session.beginTransaction()) {
		        // Nodes 데이터 처리
	            String runId = (String) ((Map<String, Object>) map.get("drawing")).get("runUuid");
		        
		        // 가장 큰 reqTimeStamp를 가진 레코드 목록 생성
//		        for (Map<String, Object> node : findMaxTimestampRecords((List<Map<String, Object>>) map.get("nodes"))) {
		        for (Map<String, Object> node : (List<Map<String, Object>>) map.get("nodes")) {
		            String nodeName = (String) node.get("name");
		            Map<String, Object> properties = (Map<String, Object>) node.get("properties");
		        	//if(!"Y".equals(properties.get("last_update"))) continue;
		        	
		        	Result result = tx.run("""
						MATCH (run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $id}) RETURN COALESCE(s.state, '') AS state
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
		        	
		        	if("add".equals(beforeState) && "del".equals(properties.get("state"))) {
			            result = tx.run("""
			            	MATCH (run:run  {uuid:$uuid}) - [:CONTAINS] -> (s:symbol {id:$id})
							DETACH DELETE s
		            		""", 
		            		Map.of(
		            				"uuid", runId,
		            				"id", nodeName
		            				)
		            		);
		        	}else{
		        		String tobeState = (String)properties.get("state");
		        		if("add".equals(beforeState)) {
		        			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
		        		}
			            List<List<Double>> position = (List<List<Double>>) node.get("position");
			
			            // Run 노드와 Symbol 노드를 찾거나 생성하고, 관계를 설정하는 쿼리
			            result = tx.run("""
			            	MATCH (d:Drawing) - [:HAS_RUN] -> (run:run  {uuid:$uuid})
							MERGE (run)-[:CONTAINS]->(s:symbol {id: $id})
							ON CREATE SET s.symbol_type = $symbolType,
			            		          s.top_x = toFloat($topX) / d.width, 
			            		          s.top_y = toFloat($topY) / d.height, 
			            		          s.bottom_x = toFloat($bottomX) / d.width, 
			            		          s.bottom_y = toFloat($bottomY) / d.height, 
			            		          s.state = $state
							ON MATCH SET  s.symbol_type = $symbolType,
			            		          s.top_x = toFloat($topX) / d.width, 
			            		          s.top_y = toFloat($topY) / d.height, 
			            		          s.bottom_x = toFloat($bottomX) / d.width, 
			            		          s.bottom_y = toFloat($bottomY) / d.height, 
			            		          s.state = $state
		                    """, 
		                    Map.of(
			                    "uuid", runId,
		                        "id", nodeName,
		                        "symbolType", properties.get("label"),
		                        "topX", position.get(0).get(0),
		                        "topY", position.get(0).get(1),
		                        "bottomX", position.get(1).get(0),
		                        "bottomY", position.get(1).get(1),
		                        //"state", tobeState
		                        "state", ""
		                    )
			             );
		        	}

				    // 통계 정보 확인
				    SummaryCounters counters = result.consume().counters();
				    System.out.println("Nodes created: " + counters.nodesCreated());
				    System.out.println("Relationships created: " + counters.relationshipsCreated());
				    System.out.println("Properties set: " + counters.propertiesSet());
		        }

		     
		        // Edges 데이터 처리
		        //for (Map<String, Object> edge : findMaxTimestampRecords((List<Map<String, Object>>) map.get("edges"))) {
		        for (Map<String, Object> edge : (List<Map<String, Object>>) map.get("edges")) {
		            Map<String, Object> properties = (Map<String, Object>) edge.get("properties");
		        	//if(!"Y".equals(properties.get("last_update"))) continue;
		            String edgeName = (String) edge.get("name");
		            String source = (String) edge.get("source");
		            String target = (String) edge.get("target");
		            
		        	Result result = tx.run("""
						MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $sourceId}) - [p:PIPED_TO]->(t:symbol {id: $targetId}) <- [:CONTAINS] - (r)
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
		        	
		        	if("add".equals(beforeState) && "del".equals(properties.get("state"))) {
			            result = tx.run("""
								MATCH (r:run {uuid:$uuid})-[:CONTAINS]->(s:symbol {id: $sourceId}) - [p:PIPED_TO]->(t:symbol {id: $targetId}) <- [:CONTAINS] - (r)
				        		DELETE p
			                    """, 
			                    Map.of(
				                    "uuid", runId,
			                        "sourceId", source,
			                        "targetId", target
			                    )
		            		);
		        	}else{
		        		String tobeState = (String)properties.get("state");
		        		if("add".equals(beforeState)) {
		        			tobeState = "add"; // 신규로 추가했던 객체는 언제나 add
		        		}
			            result = tx.run("""
				            	MATCH (run:run  {uuid:$uuid})
								MATCH (run)-[:CONTAINS]->(source:symbol {id: $sourceId})
								MATCH (run)-[:CONTAINS]->(target:symbol {id: $targetId})
			                    MERGE (source)-[r:PIPED_TO]->(target)
			                    ON CREATE SET r.id = $edgeId,
			            					  r.state = $state
			            		ON MATCH SET r.id = $edgeId,
			            					 r.state = $state
		                    """,
		                    Map.of(
			                    "uuid", runId,
		                        "sourceId", source,
		                        "targetId", target,
		                        "edgeId", edgeName,
		                        //"state", tobeState
		                        "state", ""
		                    )
		                );
		        	}

				    // 통계 정보 확인
				    SummaryCounters counters = result.consume().counters();
				    System.out.println("Nodes created: " + counters.nodesCreated());
				    System.out.println("Relationships created: " + counters.relationshipsCreated());
				    System.out.println("Properties set: " + counters.propertiesSet());
		        }
		        tx.commit();
        	}
        }
		return null;
	}
}