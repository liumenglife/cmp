package com.cmp.platform.corechain;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class Batch3ContractController {

    private final CoreChainService service;

    Batch3ContractController(CoreChainService service) {
        this.service = service;
    }

    @GetMapping("/api/batch3/shared-contract")
    Map<String, Object> sharedContract() {
        return service.batch3SharedContract();
    }

    @GetMapping("/api/batch3/contracts/{contractId}/mount-points")
    Map<String, Object> mountPoints(@PathVariable String contractId,
                                    @RequestParam(value = "signature_status", required = false) String signatureStatus) {
        return service.batch3MountPoints(contractId, signatureStatus);
    }
}
