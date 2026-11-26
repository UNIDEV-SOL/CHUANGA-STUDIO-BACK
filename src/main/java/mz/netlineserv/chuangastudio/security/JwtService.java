package mz.netlineserv.chuangastudio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String segredo;

    @Value("${jwt.expiration-ms}")
    private long expiracaoMs;

    private SecretKey chave;

    @PostConstruct
    void iniciar() {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET tem " + bytes.length
                    + " bytes; o HS256 exige pelo menos 32. Gere um com: openssl rand -base64 48");
        }
        chave = Keys.hmacShaKeyFor(bytes);
    }

    public String gerarToken(UsuarioAutenticado usuario) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoMs);

        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("id", usuario.getId().toString())
                .claim("nome", usuario.getNome())
                .claim("papel", usuario.getPapel())
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chave)
                .compact();
    }

    /**
     * Valida assinatura e validade. Lança {@link io.jsonwebtoken.ExpiredJwtException} se expirou
     * e {@link JwtException} se foi alterado ou está mal formado.
     */
    public Claims lerClaims(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** O token foi emitido antes da última troca de palavra-passe (o iat só tem precisão de segundos). */
    public boolean revogado(Claims claims, UsuarioAutenticado usuario) {
        if (usuario.getSenhaAlteradaEm() == null || claims.getIssuedAt() == null) {
            return usuario.getSenhaAlteradaEm() != null;
        }
        Instant alteradaEm = usuario.getSenhaAlteradaEm().atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS);
        return claims.getIssuedAt().toInstant().isBefore(alteradaEm);
    }
}
