package com.logicscope.app;

import com.logicscope.discovery.MavenRepositoryInspector;
import com.logicscope.discovery.RepositoryInspector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DiscoveryConfiguration {

    @Bean
    RepositoryInspector repositoryInspector() {
        return new MavenRepositoryInspector();
    }
}
