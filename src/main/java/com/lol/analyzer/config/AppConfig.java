package com.lol.analyzer.config;

import com.lol.analyzer.riot.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application-wide beans not covered by Spring Boot auto-configuration.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    /** Loads players of one analysis in parallel; Riot limits are still enforced by the client's limiter. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService riotExecutor(@Value("${analyzer.riot-threads:4}") int threads) {
        return Executors.newFixedThreadPool(threads);
    }

    @Bean
    public Region defaultRegion(@Value("${analyzer.default-region:EUW}") String code) {
        return Region.parse(code).orElse(Region.EUW);
    }
}
