package sga_backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    // Configuration de la sécurité des routes (déjà existante)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // On désactive CSRF (protection inutile pour une API REST appelée par Angular)
            .csrf(csrf -> csrf.disable())

            // On active notre configuration CORS définie ci-dessous
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .authorizeHttpRequests(auth -> auth
                // Les routes /api/auth/** restent accessibles sans authentification
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/employes/**").permitAll()    // ← couvre aussi /enfants
                .requestMatchers("/api/referentiel/**", "/api/types-conge/**",      // <-- nouveau
                                              "/api/jours-feries/**",     // <-- nouveau
                                              "/api/demandes-conge/**",
                                            "/api/soldes/**","/api/validation/**","/api/unites/**","/api/directions/**").permitAll()

                // Toutes les autres routes nécessitent d'être connecté
                .anyRequest().authenticated()
                
            );

        return http.build();
    }

    // ============================================
    // NOUVELLE CONFIGURATION CORS
    // Dit au navigateur : "j'autorise localhost:4200 à m'appeler"
    // ============================================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // On autorise UNIQUEMENT l'origine d'Angular en développement
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));

        // On autorise les méthodes HTTP qu'Angular utilisera (GET, POST, PUT, DELETE...)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // On autorise tous les en-têtes (headers) dans les requêtes
        configuration.setAllowedHeaders(List.of("*"));

        // On applique cette configuration à TOUTES les routes ("/**")
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

}