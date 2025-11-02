package com.paytrix.cipherbank.infrastructure.adapter.out.persistence.repository;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.IpWhitelist;
import com.paytrix.cipherbank.application.port.out.IpWhitelistRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JpaIpWhitelistRepository extends JpaRepository<IpWhitelist, Long>, IpWhitelistRepositoryPort {

    // Spring JPA automatically maps this to SELECT * FROM ip_whitelist WHERE active = true
    List<IpWhitelist> findAllByActiveTrue();

    @Override
    default List<IpWhitelist> findAllActive() {
        return findAllByActiveTrue();
    }
}
