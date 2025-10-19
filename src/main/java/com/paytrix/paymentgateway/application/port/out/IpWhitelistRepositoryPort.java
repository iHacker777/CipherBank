package com.paytrix.paymentgateway.application.port.out;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.IpWhitelist;
import java.util.List;

public interface IpWhitelistRepositoryPort {
    List<IpWhitelist> findAllActive();
}
