package mz.netlineserv.chuangastudio.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica o pedido pelo JWT. Um token que falha não bloqueia aqui (os endpoints públicos
 * continuam a responder); o motivo fica no pedido para o 401 dizer exatamente o que aconteceu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String cabecalhoAuth = request.getHeader("Authorization");
        if (cabecalhoAuth != null && !cabecalhoAuth.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            FalhaAutenticacao falha = autenticar(cabecalhoAuth, request);
            if (falha != null) {
                falha.registar(request);
                log.debug("Token recusado em {} {}: {}", request.getMethod(), request.getRequestURI(), falha.codigo());
            }
        }
        filterChain.doFilter(request, response);
    }

    private FalhaAutenticacao autenticar(String cabecalhoAuth, HttpServletRequest request) {
        if (!cabecalhoAuth.startsWith("Bearer ") || cabecalhoAuth.length() <= 7) {
            return FalhaAutenticacao.CABECALHO_INVALIDO;
        }
        String token = cabecalhoAuth.substring(7).trim();

        Claims claims;
        try {
            claims = jwtService.lerClaims(token);
        } catch (ExpiredJwtException ex) {
            return FalhaAutenticacao.TOKEN_EXPIRADO;
        } catch (JwtException | IllegalArgumentException ex) {
            return FalhaAutenticacao.TOKEN_INVALIDO;
        }
        if (claims.getSubject() == null) {
            return FalhaAutenticacao.TOKEN_INVALIDO;
        }

        UsuarioAutenticado usuario;
        try {
            usuario = (UsuarioAutenticado) userDetailsService.loadUserByUsername(claims.getSubject());
        } catch (UsernameNotFoundException ex) {
            return FalhaAutenticacao.CONTA_INEXISTENTE;
        }
        if (!usuario.isEnabled()) {
            return FalhaAutenticacao.CONTA_DESATIVADA;
        }
        if (jwtService.revogado(claims, usuario)) {
            return FalhaAutenticacao.SESSAO_REVOGADA;
        }

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        return null;
    }
}
