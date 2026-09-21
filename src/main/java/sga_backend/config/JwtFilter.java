package sga_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sga_backend.service.JwtService;
import sga_backend.repository.UtilisateurRepository;
import sga_backend.entity.Utilisateur;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    public JwtFilter(JwtService jwtService, UtilisateurRepository utilisateurRepository) {
        this.jwtService = jwtService;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Pas de jeton ou format incorrect -> on laisse passer (la sécurité des endpoints décidera)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String token = authHeader.substring(7);  // enlève "Bearer "
            final String login = jwtService.extraireLogin(token);

            // Si on a un login et que personne n'est encore authentifié dans ce contexte
            if (login != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Utilisateur utilisateur = utilisateurRepository.findByLogin(login).orElse(null);

                if (utilisateur != null && jwtService.estValide(token, login)) {
                    // Récupérer le rôle pour les autorisations
                    String role = utilisateur.getProfil() != null ? utilisateur.getProfil().getCode() : "USER";

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            login,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            // Jeton invalide/expiré : on log et on laisse passer (les endpoints protégés refuseront)
            System.err.println("JWT invalide : " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}