package com.logicscope.app;

import com.logicscope.source.SourceInvestigator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InvestigationConfiguration {

    @Bean
    SourceInvestigator sourceInvestigator() {
        return new SourceInvestigator();
    }
}
