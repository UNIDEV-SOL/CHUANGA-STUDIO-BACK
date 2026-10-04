package mz.netlineserv.chuangastudio.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Distrito;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.repository.DistritoRepository;
import mz.netlineserv.chuangastudio.repository.ProvinciaRepository;
import org.springframework.stereotype.Component;

/** Aplica província/distrito (opcionais) a um utilizador, garantindo que o distrito é da província. */
@Component
@RequiredArgsConstructor
public class MoradaResolver {

    private final ProvinciaRepository provinciaRepository;
    private final DistritoRepository distritoRepository;

    public void aplicar(Usuario usuario, Long provinciaId, Long distritoId) {
        if (provinciaId == null && distritoId == null) {
            return;
        }
        if (distritoId != null) {
            Distrito distrito = distritoRepository.findById(distritoId)
                    .orElseThrow(() -> new RegraNegocioException("Distrito inválido."));
            if (provinciaId != null && !distrito.getProvincia().getId().equals(provinciaId)) {
                throw new RegraNegocioException("O distrito escolhido não pertence a essa província.");
            }
            usuario.setDistrito(distrito);
            usuario.setProvincia(distrito.getProvincia());
            return;
        }
        usuario.setProvincia(provinciaRepository.findById(provinciaId)
                .orElseThrow(() -> new RegraNegocioException("Província inválida.")));
        usuario.setDistrito(null);
    }
}
