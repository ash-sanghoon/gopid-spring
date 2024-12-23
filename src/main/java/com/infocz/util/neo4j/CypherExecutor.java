package com.infocz.util.neo4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
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

    @Value("${neo4j.database}")
    private String database;

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
    
    public Session getSession() {
    	return getDriver().session(SessionConfig.builder().withDatabase(getDatabase()).build());
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
	public List<Map<String, Object>> execCyphers(String cypher, Map<String, Object> map, @SuppressWarnings("rawtypes") Function mapper) {
        try (Session session = driver.session(SessionConfig.builder().withDatabase(database).build())) {
            return session.run(cypher, map).list(mapper);
        }
    }

    @Override
	public void close() throws Exception {
		driver.close();
	}

	public String getDatabase() {
		// TODO Auto-generated method stub
		return database;
	}

	public String getRunDatabase() {
		// TODO Auto-generated method stub
		return "samsung-ena-code-test";
	}
}


