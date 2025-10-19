package com.paytrix.paymentgateway.application.service;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.IpWhitelist;
import com.paytrix.paymentgateway.application.port.out.IpWhitelistRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IpWhitelistService {

    private final IpWhitelistRepositoryPort repositoryPort;

    public IpWhitelistService(IpWhitelistRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public Set<String> getActiveIps() {

        return repositoryPort.findAllActive()
                .stream()
                .map(IpWhitelist::getIpAddress)
                .collect(Collectors.toSet());
    }
}
