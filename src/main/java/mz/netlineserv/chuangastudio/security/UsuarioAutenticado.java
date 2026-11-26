package mz.netlineserv.chuangastudio.security;

import lombok.Getter;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UsuarioAutenticado implements UserDetails {

    private final UUID id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final boolean contaAtiva;
    private final String papel;
    private final LocalDateTime senhaAlteradaEm;

    public UsuarioAutenticado(Usuario usuario) {
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.senhaHash = usuario.getSenhaHash();
        this.contaAtiva = usuario.isContaAtiva();
        this.papel = usuario.getPapel().name();
        this.senhaAlteradaEm = usuario.getSenhaAlteradaEm();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return contaAtiva;
    }
}
