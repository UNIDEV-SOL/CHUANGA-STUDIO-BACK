package mz.netlineserv.chuangastudio.modules.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.chat.domain.ChatAnexo;

public interface ChatAnexoRepository extends JpaRepository<ChatAnexo, UUID> {
}
