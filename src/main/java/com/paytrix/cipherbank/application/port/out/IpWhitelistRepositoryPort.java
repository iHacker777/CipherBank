package com.paytrix.cipherbank.application.port.out;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.IpWhitelist;
import java.util.List;

public interface IpWhitelistRepositoryPort {
    List<IpWhitelist> findAllActive();
}
