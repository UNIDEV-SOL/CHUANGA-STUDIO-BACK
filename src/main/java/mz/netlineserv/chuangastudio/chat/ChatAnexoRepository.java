package mz.netlineserv.chuangastudio.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatAnexoRepository extends JpaRepository<ChatAnexo, UUID> {
}
