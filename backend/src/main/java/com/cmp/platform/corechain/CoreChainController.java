package com.cmp.platform.corechain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CoreChainController {

    private final CoreChainService service;

    CoreChainController(CoreChainService service) {
        this.service = service;
    }

    @PostMapping("/api/contracts")
    ResponseEntity<Map<String, Object>> createContract(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createContract(request));
    }

    @GetMapping("/api/contracts")
    Map<String, Object> contractLedger(@RequestParam(required = false) String keyword) {
        return service.contractLedger(keyword);
    }

    @GetMapping("/api/contracts/{contractId}")
    Map<String, Object> contractDetail(@PathVariable String contractId) {
        return service.contractDetail(contractId);
    }

    @GetMapping("/api/contracts/{contractId}/master")
    Map<String, Object> contractMaster(@PathVariable String contractId) {
        return service.contractMaster(contractId);
    }

    @PatchMapping("/api/contracts/{contractId}")
    ResponseEntity<Map<String, Object>> editContract(@PathVariable String contractId,
                                                     @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                     @RequestBody Map<String, Object> request) {
        return service.editContract(contractId, permissions, request);
    }

    @PostMapping("/api/document-center/assets")
    ResponseEntity<Map<String, Object>> createDocumentAsset(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDocumentAsset(request));
    }

    @PostMapping("/api/workflow-engine/processes")
    ResponseEntity<Map<String, Object>> startProcess(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.startProcess(request));
    }

    @PostMapping("/api/workflow-engine/processes/{processId}/results")
    Map<String, Object> completeProcess(@PathVariable String processId, @RequestBody Map<String, Object> request) {
        return service.completeProcess(processId, request);
    }
}

@org.springframework.stereotype.Service
class CoreChainService {

    private final Map<String, ContractState> contracts = new ConcurrentHashMap<>();
    private final Map<String, ProcessState> processes = new ConcurrentHashMap<>();

    Map<String, Object> createContract(Map<String, Object> request) {
        String contractId = "ctr-" + UUID.randomUUID();
        String contractNo = "CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ContractState contract = new ContractState(contractId, contractNo, text(request, "contract_name", "未命名合同"), "DRAFT",
                text(request, "owner_org_unit_id", null), text(request, "owner_user_id", null), text(request, "amount", null),
                text(request, "currency", null), null, null, null, new ArrayList<>());
        contract.events().add(event("CONTRACT_CREATED", contractId, text(request, "trace_id", null)));
        contracts.put(contractId, contract);
        return contractBody(contract);
    }

    ResponseEntity<Map<String, Object>> editContract(String contractId, String permissions, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        if (permissions == null || !permissions.contains("CONTRACT_EDIT")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("PERMISSION_DENIED", "缺少合同编辑权限"));
        }
        if (!"DRAFT".equals(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONTRACT_STATUS_CONFLICT", "仅草稿状态允许编辑"));
        }

        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), text(request, "contract_name", contract.contractName()),
                contract.contractStatus(), contract.ownerOrgUnitId(), contract.ownerUserId(), text(request, "amount", contract.amount()),
                text(request, "currency", contract.currency()), contract.currentDocument(), contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event("CONTRACT_UPDATED", contractId, text(request, "trace_id", null)));
        contracts.put(contractId, updated);
        return ResponseEntity.ok(contractBody(updated));
    }

    Map<String, Object> createDocumentAsset(Map<String, Object> request) {
        String contractId = text(request, "owner_id", null);
        ContractState contract = requireContract(contractId);
        DocumentRef document = new DocumentRef("doc-asset-" + UUID.randomUUID(), "doc-ver-" + UUID.randomUUID(), "FIRST_VERSION_WRITTEN");
        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contract.contractStatus(),
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), document,
                contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event("DOCUMENT_BOUND", document.documentAssetId(), text(request, "trace_id", null)));
        contracts.put(contractId, updated);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contractId);
        body.put("document_asset_id", document.documentAssetId());
        body.put("document_version_id", document.documentVersionId());
        body.put("document_status", document.documentStatus());
        return body;
    }

    Map<String, Object> startProcess(Map<String, Object> request) {
        String contractId = text(request, "contract_id", null);
        ContractState contract = requireContract(contractId);
        String documentAssetId = text(request, "document_asset_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        if (contract.currentDocument() == null
                || !documentAssetId.equals(contract.currentDocument().documentAssetId())
                || !documentVersionId.equals(contract.currentDocument().documentVersionId())) {
            throw new IllegalArgumentException("审批发起必须消费合同当前文档引用");
        }

        String processId = "proc-" + UUID.randomUUID();
        Map<String, Object> approvalSummary = approvalSummary(processId, "STARTED", null);
        ProcessState process = new ProcessState(processId, contractId, documentAssetId, documentVersionId, "STARTED", approvalSummary);
        processes.put(processId, process);

        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), "UNDER_APPROVAL",
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                approvalSummary, processId, copyEvents(contract.events()));
        updated.events().add(event("APPROVAL_STARTED", processId, text(request, "trace_id", null)));
        contracts.put(contractId, updated);
        return processBody(process);
    }

    Map<String, Object> completeProcess(String processId, Map<String, Object> request) {
        ProcessState process = requireProcess(processId);
        ContractState contract = requireContract(process.contractId());
        String result = text(request, "result", "APPROVED");
        String processStatus = switch (result) {
            case "APPROVED" -> "COMPLETED";
            case "REJECTED" -> "REJECTED";
            case "TERMINATED" -> "TERMINATED";
            default -> "COMPENSATING";
        };
        String contractStatus = switch (processStatus) {
            case "COMPLETED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "TERMINATED" -> "APPROVAL_TERMINATED";
            default -> contract.contractStatus();
        };
        Map<String, Object> approvalSummary = approvalSummary(processId, processStatus, result);
        ProcessState updatedProcess = new ProcessState(processId, process.contractId(), process.documentAssetId(), process.documentVersionId(), processStatus, approvalSummary);
        processes.put(processId, updatedProcess);

        ContractState updatedContract = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contractStatus,
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                approvalSummary, processId, copyEvents(contract.events()));
        updatedContract.events().add(event(eventType(processStatus), processId, text(request, "trace_id", null)));
        contracts.put(contract.contractId(), updatedContract);
        return processBody(updatedProcess);
    }

    Map<String, Object> contractMaster(String contractId) {
        return contractBody(requireContract(contractId));
    }

    Map<String, Object> contractLedger(String keyword) {
        List<Map<String, Object>> items = contracts.values().stream()
                .filter(contract -> keyword == null || keyword.isBlank() || contract.contractName().contains(keyword) || contract.contractNo().contains(keyword))
                .map(this::ledgerBody)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    Map<String, Object> contractDetail(String contractId) {
        ContractState contract = requireContract(contractId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_master", contractBody(contract));
        body.put("document_summary", contract.currentDocument() == null ? null : documentBody(contract.currentDocument()));
        body.put("approval_summary", contract.approvalSummary());
        body.put("timeline_summary", contract.events());
        return body;
    }

    private Map<String, Object> contractBody(ContractState contract) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contract.contractId());
        body.put("contract_no", contract.contractNo());
        body.put("contract_name", contract.contractName());
        body.put("contract_status", contract.contractStatus());
        body.put("owner_org_unit_id", contract.ownerOrgUnitId());
        body.put("owner_user_id", contract.ownerUserId());
        body.put("amount", contract.amount());
        body.put("currency", contract.currency());
        body.put("current_document", contract.currentDocument() == null ? null : documentBody(contract.currentDocument()));
        body.put("approval_summary", contract.approvalSummary());
        body.put("timeline_event", contract.events());
        body.put("audit_record", contract.events());
        return body;
    }

    private Map<String, Object> ledgerBody(ContractState contract) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contract.contractId());
        body.put("contract_no", contract.contractNo());
        body.put("contract_name", contract.contractName());
        body.put("contract_status", contract.contractStatus());
        body.put("owner_org_unit_id", contract.ownerOrgUnitId());
        body.put("owner_user_id", contract.ownerUserId());
        body.put("approval_summary", contract.approvalSummary());
        body.put("current_document", contract.currentDocument() == null ? null : documentBody(contract.currentDocument()));
        return body;
    }

    private Map<String, Object> error(String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", errorCode);
        body.put("message", message);
        return body;
    }

    private Map<String, Object> processBody(ProcessState process) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("process_id", process.processId());
        body.put("contract_id", process.contractId());
        body.put("document_asset_id", process.documentAssetId());
        body.put("document_version_id", process.documentVersionId());
        body.put("process_status", process.processStatus());
        body.put("approval_summary", process.approvalSummary());
        return body;
    }

    private Map<String, Object> documentBody(DocumentRef document) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_asset_id", document.documentAssetId());
        body.put("document_version_id", document.documentVersionId());
        body.put("document_status", document.documentStatus());
        return body;
    }

    private Map<String, Object> approvalSummary(String processId, String processStatus, String result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("process_id", processId);
        summary.put("process_status", processStatus);
        summary.put("result", result);
        return summary;
    }

    private Map<String, Object> event(String eventType, String objectId, String traceId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", eventType);
        event.put("object_id", objectId);
        event.put("summary", eventSummary(eventType));
        event.put("trace_id", traceId);
        event.put("occurred_at", Instant.now().toString());
        return event;
    }

    private String eventSummary(String eventType) {
        return switch (eventType) {
            case "CONTRACT_CREATED" -> "合同主档已创建";
            case "DOCUMENT_BOUND" -> "文档已绑定合同当前主版本";
            case "APPROVAL_STARTED" -> "审批已发起并回写合同状态";
            case "APPROVAL_APPROVED" -> "审批通过并回写合同状态";
            case "APPROVAL_REJECTED" -> "审批驳回并回写合同状态";
            case "APPROVAL_TERMINATED" -> "审批终止并回写合同状态";
            default -> "审批回写进入补偿";
        };
    }

    private String eventType(String processStatus) {
        return switch (processStatus) {
            case "COMPLETED" -> "APPROVAL_APPROVED";
            case "REJECTED" -> "APPROVAL_REJECTED";
            case "TERMINATED" -> "APPROVAL_TERMINATED";
            default -> "APPROVAL_WRITEBACK_COMPENSATING";
        };
    }

    private ContractState requireContract(String contractId) {
        ContractState contract = contracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("contract_id 不存在: " + contractId);
        }
        return contract;
    }

    private ProcessState requireProcess(String processId) {
        ProcessState process = processes.get(processId);
        if (process == null) {
            throw new IllegalArgumentException("process_id 不存在: " + processId);
        }
        return process;
    }

    private List<Map<String, Object>> copyEvents(List<Map<String, Object>> events) {
        return new ArrayList<>(events);
    }

    private String text(Map<String, Object> request, String field, String defaultValue) {
        Object value = request.get(field);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private record ContractState(String contractId, String contractNo, String contractName, String contractStatus,
                                 String ownerOrgUnitId, String ownerUserId, String amount, String currency,
                                 DocumentRef currentDocument, Map<String, Object> approvalSummary, String processId,
                                 List<Map<String, Object>> events) {
    }

    private record DocumentRef(String documentAssetId, String documentVersionId, String documentStatus) {
    }

    private record ProcessState(String processId, String contractId, String documentAssetId, String documentVersionId,
                                String processStatus, Map<String, Object> approvalSummary) {
    }
}
