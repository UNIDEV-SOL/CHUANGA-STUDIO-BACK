package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmailAndDeletadoFalse(String email);

    boolean existsByEmailAndDeletadoFalse(String email);

    List<Usuario> findByPapelAndDeletadoFalseOrderByCriadoEmDesc(PapelUsuario papel);

    long countByPapelAndContaAtivaTrueAndDeletadoFalse(PapelUsuario papel);
}
