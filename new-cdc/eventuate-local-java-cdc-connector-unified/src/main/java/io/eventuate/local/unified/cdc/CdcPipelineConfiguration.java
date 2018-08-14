package io.eventuate.local.unified.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.local.common.PublishedEvent;
import io.eventuate.local.unified.cdc.factory.CdcPipelineFactory;
import io.eventuate.local.unified.cdc.pipeline.CdcPipeline;
import io.eventuate.local.unified.cdc.properties.CdcPipelineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;

@Configuration
@Import(CdcDefaultPipelinePropertiesConfiguration.class)
public class CdcPipelineConfiguration {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private ObjectMapper objectMapper = new ObjectMapper();
  private List<CdcPipeline> cdcPipelines = new ArrayList<>();

  @Value("${eventuate.cdc.pipeline.properties:#{null}}")
  private String cdcPipelineJsonProperties;

  @Autowired
  private Collection<CdcPipelineFactory> cdcPipelineFactories;

  @Autowired
  private CdcPipelineProperties defaultCdcPipelineProperties;

  @PostConstruct
  public void initialize() {
    logger.info("Starting unified cdc pipelines");

    Optional
            .ofNullable(cdcPipelineJsonProperties)
            .map(this::convertCdcPipelinePropertiesToListOfMaps)
            .orElseGet(() -> {
              createStartSaveCdcDefaultPipeline(defaultCdcPipelineProperties);
              return Collections.emptyList();
            })
            .forEach(this::createStartSaveCdcPipeline);

    logger.info("Unified cdc pipelines are started");
  }

  @PreDestroy
  public void stop() {
    cdcPipelines.forEach(CdcPipeline::stop);
  }

  private List<Map<String, Object>> convertCdcPipelinePropertiesToListOfMaps(String properties) {
    try {
      return objectMapper.readValue(properties, List.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void createStartSaveCdcPipeline(Map<String, Object> properties) {

    CdcPipeline cdcPipeline = createCdcPipeline(properties);
    cdcPipeline.start();
    cdcPipelines.add(cdcPipeline);
  }

  private void createStartSaveCdcDefaultPipeline(CdcPipelineProperties cdcDefaultPipelineProperties) {

    cdcDefaultPipelineProperties.validate();
    CdcPipeline cdcPipeline = createCdcPipeline(cdcDefaultPipelineProperties);
    cdcPipeline.start();
    cdcPipelines.add(cdcPipeline);
  }

  private CdcPipeline createCdcPipeline(Map<String, Object> properties) {

    CdcPipelineProperties cdcPipelineProperties = objectMapper.convertValue(properties, CdcPipelineProperties.class);
    cdcPipelineProperties.validate();

    CdcPipelineFactory<? extends CdcPipelineProperties, PublishedEvent> cdcPipelineFactory = findCdcPipelineFactory(cdcPipelineProperties.getType());

    CdcPipelineProperties exactCdcPipelineProperties = objectMapper.convertValue(properties, cdcPipelineFactory.propertyClass());
    exactCdcPipelineProperties.validate();

    return  ((CdcPipelineFactory)cdcPipelineFactory).create(exactCdcPipelineProperties);
  }

  private CdcPipeline<PublishedEvent> createCdcPipeline(CdcPipelineProperties cdcPipelineProperties) {
    CdcPipelineFactory<? extends CdcPipelineProperties, PublishedEvent> cdcPipelineFactory = findCdcPipelineFactory(cdcPipelineProperties.getType());

    return  ((CdcPipelineFactory)cdcPipelineFactory).create(cdcPipelineProperties);
  }

  private CdcPipelineFactory<? extends CdcPipelineProperties, PublishedEvent> findCdcPipelineFactory(String type) {
    return cdcPipelineFactories
            .stream()
            .filter(factory ->  factory.supports(type))
            .findAny()
            .orElseThrow(() ->
                    new RuntimeException(String.format("factory not found for type %s",
                            type)));
  }
}
