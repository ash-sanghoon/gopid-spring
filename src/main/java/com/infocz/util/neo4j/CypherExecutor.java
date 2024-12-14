package com.infocz.util.neo4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class CypherExecutor implements AutoCloseable{
    // Inject properties from application.properties
    @Value("${neo4j.uri}")
    private String uri;

    @Value("${neo4j.username}")
    private String username;

    @Value("${neo4j.password}")
    private String password;

    private Driver driver;

    // Initialization method - called after all properties are set
    @PostConstruct
    public void initialize() {
        // Configure connection pool
        Config config = Config.builder()
            .withMaxConnectionPoolSize(100)
            .withConnectionAcquisitionTimeout(30, TimeUnit.SECONDS)
            .withMaxConnectionLifetime(1, TimeUnit.HOURS)
            .build();

        // Create the driver
        this.driver = GraphDatabase.driver(uri, 
            AuthTokens.basic(username, password), 
            config
        );

        System.out.println("Neo4j Driver initialized successfully");
    }

    // Method to get the driver
    public Driver getDriver() {
        if (driver == null) {
            throw new IllegalStateException("Neo4j Driver not initialized");
        }
        return driver;
    }

    // Clean up method - called when the Spring context is closed
    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            driver.close();
            System.out.println("Neo4j Driver closed");
        }
    }

    @SuppressWarnings("unchecked")
	public <T> List<T> readCyphers(String cypher, Map<String, Object> map, String database) {
//		try (var session = driver.session(SessionConfig.builder().withDatabase("samsung-ena").build())) {
//      public <T> List<T> readCyphers(String cypher, Map map, Function<org.neo4j.driver.Record, T> mapper) {
//        try (Session session = driver.session()) {
        try (Session session = driver.session(SessionConfig.builder().withDatabase(database).build())) {
            Result result = session.run(cypher, map);
            return (List<T>) result.list(record -> record.asMap());
        }
    }
//    
//    public <T> List<T> readCyphers1(String cypher, Function<Record, T> mapper) {
//        try (Session session = driver.session()) {
//            return (List<T>) session.readTransaction(tx -> tx.run(cypher).list(mapper));
//        }
//    }
	@Override
	public void close() throws Exception {
		driver.close();
	}
}


