package com.infocz.util.neo4j;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.TransactionManager;

@Configuration
public class Neo4jConfig {
    
    @Value("${neo4j.uri}")
    private String uri;
    
    @Value("${neo4j.username}")
    private String username;
    
    @Value("${neo4j.password}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri,
                AuthTokens.basic(username, password));
    }
    @Bean
    public TransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}