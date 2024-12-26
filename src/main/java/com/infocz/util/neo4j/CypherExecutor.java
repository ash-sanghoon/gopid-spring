package com.infocz.util.neo4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;

@Component
public class CypherExecutor implements AutoCloseable{

    private final Driver neo4jDriver;

    public CypherExecutor(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }
    
    private static final String RESOURCE_KEY = "NEO4J_SESSION";
    
    @Value("${neo4j.database}")
    private String database;

    // Method to get the driver
    public Driver getDriver() {
        if (neo4jDriver == null) {
            throw new IllegalStateException("Neo4j Driver not initialized");
        }
        return neo4jDriver;
    }
    
	public Session getSession() {
    	
        Session session = (Session) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
            
        if (session == null) {
            Session newSession = neo4jDriver.session(SessionConfig.builder()
                .withDatabase(getDatabase())
                .build());
            
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, newSession);
            
            // TransactionSynchronization의 모든 메소드 구현
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                    newSession.close();
                }
            });
            
            return newSession;
        }
        
        return session;
    }

    // Clean up method - called when the Spring context is closed
    @PreDestroy
    public void cleanup() {
        if (neo4jDriver != null) {
        	neo4jDriver.close();
            System.out.println("Neo4j Driver closed");
        }
    }

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> execCyphers(String cypher, Map<String, Object> map, @SuppressWarnings("rawtypes") Function mapper) {
        try (Session session = neo4jDriver.session(SessionConfig.builder().withDatabase(database).build())) {
            return session.run(cypher, map).list(mapper);
        }
    }

    @Override
	public void close() throws Exception {
    	neo4jDriver.close();
	}

	public String getDatabase() {
		return database;
	}

	public Driver getNeo4jDriver() {
		return neo4jDriver;
	}
}


