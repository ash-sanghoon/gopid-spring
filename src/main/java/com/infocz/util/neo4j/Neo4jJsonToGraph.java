package com.infocz.util.neo4j;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Neo4jJsonToGraph {

    private final Driver driver;

    public Neo4jJsonToGraph(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public void close() {
        driver.close();
    }

    public void insertData(Map<String, Object> jsonData) {
        try (Session session = driver.session(SessionConfig.builder().withDatabase("samsung-ena-code-test").build())) {
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) jsonData.get("nodes");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) jsonData.get("edges");

            // Insert nodes
            for (Map<String, Object> node : nodes) {
                session.writeTransaction(tx -> createNode(tx, node));
            }

            // Insert edges
            for (Map<String, Object> edge : edges) {
                session.writeTransaction(tx -> createEdge(tx, edge));
            }
        }
    }

    private Void createNode(Transaction tx, Map<String, Object> node) {
        String query = """
            CREATE (n:symbol {
                id: $id,
                symbol_type: $symbol_type,
                top_x: $top_x,
                top_y: $top_y,
                bottom_x: $bottom_x,
                bottom_y: $bottom_y,
                develop:'Y'
            })
            """;

        Map<String, Object> properties = (Map<String, Object>) node.get("properties");
        List<List<Integer>> position = (List<List<Integer>>) node.get("position");

        tx.run(query, Map.of(
                "id", node.get("name"),
                "symbol_type", properties.get("label"),
                "top_x", position.get(0).get(0),
                "top_y", position.get(0).get(1),
                "bottom_x", position.get(1).get(0),
                "bottom_y", position.get(1).get(1)
        ));
        return null;
    }

    private Void createEdge(Transaction tx, Map<String, Object> edge) {
        String query = """
            MATCH (source:Symbol {id: $source}),
                  (target:Symbol {id: $target})
            CREATE (source)-[r:piped_to {id: $id}]->(target)
            """;

        tx.run(query, Map.of(
                "id", edge.get("name"),
                "source", edge.get("source"),
                "target", edge.get("target")
        ));
        return null;
    }

    public static void main(String[] args) throws IOException {
    	

        String filePath = "your_json_file.json"; // 파일 경로 설정
        Map<String, Object> jsonData = readJsonFromFile("json.txt");

    	
        // Neo4j connection details
        String uri = "bolt://121.134.230.246:57867"; // Replace with your Neo4j URI
        String user = "neo4j"; // Replace with your username
        String password = "infocz4ever"; // Replace with your password

        // Example JSON data
//        Map<String, Object> jsonData = Map.of(
//            "nodes", List.of(
//                Map.of(
//                    "name", "Node1",
//                    "properties", Map.of("label", "globe valve"),
//                    "position", List.of(List.of(300, 300), List.of(400, 450))
//                ),
//                Map.of(
//                    "name", "Node2",
//                    "properties", Map.of("label", "ball valve"),
//                    "position", List.of(List.of(450, 250), List.of(600, 400))
//                )
//            ),
//            "edges", List.of(
//                Map.of(
//                    "name", "Edge1",
//                    "source", "Node1",
//                    "target", "Node2"
//                )
//            )
//        );

        // Create an instance of the Neo4jJsonToGraph class and insert the data
        Neo4jJsonToGraph neo4j = new Neo4jJsonToGraph(uri, user, password);
        neo4j.insertData(jsonData);
        neo4j.close();
    }
    public static Map<String, Object> readJsonFromFile(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(filePath), Map.class);
    }
}
