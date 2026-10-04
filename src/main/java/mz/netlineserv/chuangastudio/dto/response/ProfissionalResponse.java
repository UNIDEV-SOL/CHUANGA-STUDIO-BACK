package mz.netlineserv.chuangastudio.dto.response;

import mz.netlineserv.chuangastudio.domain.entity.Profissional;

import java.util.UUID;

public record ProfissionalResponse(UUID id, String nome, String telefone, boolean ativo, int ordem) {

    public static ProfissionalResponse deEntidade(Profissional p) {
        return new ProfissionalResponse(p.getId(), p.getNome(), p.getTelefone(), p.isAtivo(), p.getOrdem());
    }

    /** Na reserva pública só interessa quem é (sem telefone). */
    public static ProfissionalResponse publica(Profissional p) {
        return new ProfissionalResponse(p.getId(), p.getNome(), null, true, p.getOrdem());
    }
}
