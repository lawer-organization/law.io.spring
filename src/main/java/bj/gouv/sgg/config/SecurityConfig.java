package bj.gouv.sgg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Configuration de sécurité Spring Security
 * 
 * Sécurise les endpoints API avec authentification basique HTTP
 * Les credentials sont externalisés via variables d'environnement
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Value("${SECURITY_USER_NAME:admin}")
    private String username;
    
    @Value("${SECURITY_USER_PASSWORD:changeme}")
    private String password;
    
    @Value("${SECURITY_ENABLED:true}")
    private boolean securityEnabled;
    
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Configuration CORS
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        
        if (!securityEnabled) {
            // Mode développement - Désactive la sécurité
            http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }
        
        // Mode production - Sécurité activée
        http
            .authorizeHttpRequests(auth -> auth
                // Endpoints publics (santé basique uniquement)
                .requestMatchers("/actuator/health").permitAll()
                
                // Swagger/OpenAPI (protégé)
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).authenticated()
                
                // Actuator (protégé)
                .requestMatchers("/actuator/**").authenticated()
                
                // API Batch (protégé)
                .requestMatchers("/api/**").authenticated()
                
                // Tout le reste nécessite authentification
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable); // Désactivé pour API REST
        
        return http.build();
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username(username)
            .password(passwordEncoder().encode(password))
            .roles("ADMIN")
            .build();
        
        return new InMemoryUserDetailsManager(user);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Origines autorisées (React dev + production)
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        
        // Méthodes HTTP autorisées
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Headers autorisés
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Permettre les credentials (cookies, auth headers)
        configuration.setAllowCredentials(true);
        
        // Durée de cache des preflight requests (1 heure)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
