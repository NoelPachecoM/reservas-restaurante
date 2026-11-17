package com.example.reservas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Paginas que pueden llamar a esta API desde el navegador. La lista viene de cors.origenes
 * (variable CORS_ORIGENES en Render), separada por comas, por ejemplo:
 *
 *   https://paginapro.store,https://www.paginapro.store,https://*.vercel.app
 *
 * Se usa allowedOriginPatterns (y no allowedOrigins) para poder admitir comodines como
 * https://*.vercel.app, que son las direcciones de las vistas previas de cada despliegue.
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${cors.origenes}") String origenes) {
        String[] lista = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(lista)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
