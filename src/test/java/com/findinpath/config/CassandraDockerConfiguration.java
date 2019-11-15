package com.findinpath.config;


import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean;
import org.springframework.data.cassandra.config.CassandraSessionFactoryBean;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.AsyncCassandraOperations;
import org.springframework.data.cassandra.core.AsyncCassandraTemplate;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.testcontainers.containers.CassandraContainer;

@Configuration
public class CassandraDockerConfiguration {

  private static final String CASSANDRA_DOCKER_IMAGE_VERSION = ":3.11";
  private static final String KEYSPACE = "demo";
  private static final String CASSANDRA_INIT_SCRIPT = "demo.cql";

  @Bean
  public CassandraContainer cassandraContainer() {
    var cassandraContainer = new CassandraContainer(CassandraContainer.IMAGE
        + CASSANDRA_DOCKER_IMAGE_VERSION);
    cassandraContainer.start();

    setupSchema(cassandraContainer);
    return cassandraContainer;
  }

  @Bean
  public CassandraClusterFactoryBean applicationCluster(CassandraContainer cassandraContainer) {
    final CassandraClusterFactoryBean cluster = new CassandraClusterFactoryBean();

    cluster.setContactPoints(cassandraContainer.getContainerIpAddress());
    cluster.setPort(cassandraContainer.getFirstMappedPort());
    return cluster;
  }

  @Bean
  public CassandraMappingContext cassandraMapping() {
    return new CassandraMappingContext();
  }


  @Bean
  public CassandraMappingContext mappingContext() {
    return new CassandraMappingContext();
  }

  @Bean
  public CassandraConverter cassandraConverter() {
    return new MappingCassandraConverter(mappingContext());
  }

  @Bean
  public CassandraSessionFactoryBean cassandraSessionFactoryBean(
      CassandraClusterFactoryBean applicationCluster) {

    Cluster cluster = applicationCluster.getObject();
    CassandraSessionFactoryBean session = new CassandraSessionFactoryBean();
    session.setCluster(cluster);
    session.setKeyspaceName(KEYSPACE);
    session.setConverter(cassandraConverter());
    session.setSchemaAction(SchemaAction.NONE);

    return session;
  }

  @Bean
  public CassandraOperations cassandraTemplate(
      CassandraSessionFactoryBean cassandraSessionFactoryBean) {
    return new CassandraTemplate(cassandraSessionFactoryBean.getObject());
  }

  @Bean
  public AsyncCassandraOperations asyncCassandraOperations(
      CassandraSessionFactoryBean cassandraSessionFactoryBean) {
    return new AsyncCassandraTemplate(cassandraSessionFactoryBean.getObject());
  }

  private void setupSchema(CassandraContainer cassandraContainer) {
    ClassPathCQLDataSet dataSet = new ClassPathCQLDataSet(CASSANDRA_INIT_SCRIPT, KEYSPACE);
    Session session = cassandraContainer.getCluster().connect();
    CQLDataLoader dataLoader = new CQLDataLoader(session);
    dataLoader.load(dataSet);
    session.close();
  }

}