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

    @PostMapping("/api/contracts/{contractId}/approvals")
    ResponseEntity<Map<String, Object>> startContractApproval(@PathVariable String contractId,
                                                              @RequestBody Map<String, Object> request) {
        return service.startContractApprovalResponse(contractId, request);
    }

    @PostMapping("/api/document-center/assets")
    ResponseEntity<Map<String, Object>> createDocumentAsset(@RequestBody Map<String, Object> request) {
        return service.createDocumentAssetResponse(request);
    }

    @GetMapping("/api/document-center/assets/{documentAssetId}")
    Map<String, Object> documentAsset(@PathVariable String documentAssetId) {
        return service.documentAsset(documentAssetId);
    }

    @GetMapping("/api/document-center/assets")
    Map<String, Object> documentAssets(@RequestParam(required = false) String owner_type,
                                       @RequestParam(required = false) String owner_id) {
        return service.documentAssets(owner_type, owner_id);
    }

    @PostMapping("/api/document-center/assets/{documentAssetId}/versions")
    ResponseEntity<Map<String, Object>> appendDocumentVersion(@PathVariable String documentAssetId,
                                                             @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.appendDocumentVersion(documentAssetId, request));
    }

    @GetMapping("/api/document-center/assets/{documentAssetId}/versions")
    Map<String, Object> documentVersions(@PathVariable String documentAssetId) {
        return service.documentVersions(documentAssetId);
    }

    @GetMapping("/api/document-center/versions/{documentVersionId}")
    Map<String, Object> documentVersion(@PathVariable String documentVersionId) {
        return service.documentVersion(documentVersionId);
    }

    @PostMapping("/api/document-center/versions/{documentVersionId}/activate")
    Map<String, Object> activateDocumentVersion(@PathVariable String documentVersionId,
                                                 @RequestBody(required = false) Map<String, Object> request) {
        return service.activateDocumentVersion(documentVersionId, request == null ? Map.of() : request);
    }

    @PostMapping("/api/approval-engine/process-definitions")
    ResponseEntity<Map<String, Object>> createProcessDefinition(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProcessDefinition(request));
    }

    @PostMapping("/api/approval-engine/process-definitions/{definitionId}/publish")
    ResponseEntity<Map<String, Object>> publishProcessDefinition(@PathVariable String definitionId,
                                                                 @RequestBody Map<String, Object> request) {
        return service.publishProcessDefinition(definitionId, request);
    }

    @PostMapping("/api/approval-engine/process-definitions/{definitionId}/disable")
    ResponseEntity<Map<String, Object>> disableProcessDefinition(@PathVariable String definitionId,
                                                                 @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.ok(service.disableProcessDefinition(definitionId, request == null ? Map.of() : request));
    }

    @PostMapping("/api/approval-engine/processes")
    ResponseEntity<Map<String, Object>> startApprovalProcess(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.startApprovalProcess(request));
    }

    @GetMapping("/api/approval-engine/tasks")
    Map<String, Object> approvalTasks(@RequestParam(required = false) String process_id,
                                      @RequestParam(required = false) String task_status) {
        return service.approvalTasks(process_id, task_status);
    }

    @PostMapping("/api/approval-engine/processes/{processId}/actions")
    Map<String, Object> submitApprovalAction(@PathVariable String processId, @RequestBody Map<String, Object> request) {
        return service.submitApprovalAction(processId, request);
    }

    @PostMapping("/api/workflow-engine/processes")
    ResponseEntity<Map<String, Object>> startProcess(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.startProcess(request));
    }

    @PostMapping("/api/workflow-engine/processes/{processId}/results")
    Map<String, Object> completeProcess(@PathVariable String processId, @RequestBody Map<String, Object> request) {
        return service.completeProcess(processId, request);
    }

    @PostMapping("/api/workflow-engine/approvals/{processId}/results")
    Map<String, Object> completeApprovalResult(@PathVariable String processId, @RequestBody Map<String, Object> request) {
        return service.completeProcess(processId, request);
    }

    @GetMapping("/api/workflow-engine/approvals/{processId}/summary")
    Map<String, Object> approvalSummary(@PathVariable String processId) {
        return service.approvalSummaryByProcess(processId);
    }

    @PostMapping("/api/workflow-engine/oa/callbacks")
    ResponseEntity<Map<String, Object>> acceptOaCallback(@RequestBody Map<String, Object> request) {
        Map<String, Object> body = service.acceptOaCallback(request);
        HttpStatus status = "WRITEBACK_COMPENSATING".equals(body.get("callback_result")) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/api/workflow-engine/compensation-tasks")
    Map<String, Object> compensationTasks(@RequestParam(required = false) String contract_id) {
        return service.compensationTasks(contract_id);
    }

    @PostMapping("/api/contracts/{contractId}/signatures/apply")
    ResponseEntity<Map<String, Object>> applySignature(@PathVariable String contractId,
                                                       @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       @RequestBody Map<String, Object> request) {
        return service.applySignature(contractId, permissions, idempotencyKey, request);
    }

    @GetMapping("/api/signature-requests/{signatureRequestId}")
    Map<String, Object> signatureRequest(@PathVariable String signatureRequestId) {
        return service.signatureRequest(signatureRequestId);
    }

    @PostMapping("/api/signature-requests/{signatureRequestId}/sessions")
    ResponseEntity<Map<String, Object>> createSignatureSession(@PathVariable String signatureRequestId,
                                                               @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSignatureSession(signatureRequestId, request));
    }

    @PostMapping("/api/signature-sessions/{signatureSessionId}/callbacks")
    ResponseEntity<Map<String, Object>> acceptSignatureCallback(@PathVariable String signatureSessionId,
                                                                @RequestBody Map<String, Object> request) {
        return service.acceptSignatureCallback(signatureSessionId, request);
    }

    @PostMapping("/api/signature-sessions/{signatureSessionId}/expire")
    Map<String, Object> expireSignatureSession(@PathVariable String signatureSessionId,
                                               @RequestBody(required = false) Map<String, Object> request) {
        return service.expireSignatureSession(signatureSessionId, request == null ? Map.of() : request);
    }

    @PostMapping("/api/signature-sessions/{signatureSessionId}/results")
    ResponseEntity<Map<String, Object>> writeBackSignatureResult(@PathVariable String signatureSessionId,
                                                                 @RequestBody Map<String, Object> request) {
        return service.writeBackSignatureResult(signatureSessionId, request);
    }

    @PostMapping("/api/contracts/{contractId}/signatures/summary/rebuild")
    Map<String, Object> rebuildSignatureSummary(@PathVariable String contractId,
                                                @RequestBody(required = false) Map<String, Object> request) {
        return service.rebuildSignatureSummary(contractId, request == null ? Map.of() : request);
    }

    @PostMapping("/api/contracts/{contractId}/paper-signature-records")
    ResponseEntity<Map<String, Object>> createPaperSignatureRecord(@PathVariable String contractId,
                                                                    @RequestBody Map<String, Object> request) {
        return service.createPaperSignatureRecord(contractId, request);
    }

    @PostMapping("/api/encrypted-documents/check-ins")
    ResponseEntity<Map<String, Object>> acceptEncryptionCheckIn(@RequestBody Map<String, Object> request) {
        return service.acceptEncryptionCheckInResponse(request);
    }

    @PostMapping("/api/encrypted-documents/access")
    ResponseEntity<Map<String, Object>> requestDecryptAccess(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                             @RequestBody Map<String, Object> request) {
        return service.requestDecryptAccess(permissions, request);
    }

    @PostMapping("/api/encrypted-documents/access/{decryptAccessId}/expire")
    ResponseEntity<Map<String, Object>> expireDecryptAccess(@PathVariable String decryptAccessId,
                                                            @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.ok(service.expireDecryptAccess(decryptAccessId, request == null ? Map.of() : request));
    }

    @PostMapping("/api/encrypted-documents/access/{decryptAccessId}/revoke")
    ResponseEntity<Map<String, Object>> revokeDecryptAccess(@PathVariable String decryptAccessId,
                                                            @RequestBody(required = false) Map<String, Object> request) {
        return ResponseEntity.ok(service.revokeDecryptAccess(decryptAccessId, request == null ? Map.of() : request));
    }

    @PostMapping("/api/encrypted-documents/access/{decryptAccessId}/consume")
    ResponseEntity<Map<String, Object>> consumeDecryptAccess(@PathVariable String decryptAccessId,
                                                              @RequestBody(required = false) Map<String, Object> request) {
        return service.consumeDecryptAccess(decryptAccessId, request == null ? Map.of() : request);
    }

    @PostMapping("/api/encrypted-documents/download-authorizations")
    ResponseEntity<Map<String, Object>> grantDownloadAuthorization(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                   @RequestBody Map<String, Object> request) {
        return service.grantDownloadAuthorization(permissions, request);
    }

    @PostMapping("/api/encrypted-documents/download-authorizations/{authorizationId}/revoke")
    ResponseEntity<Map<String, Object>> revokeDownloadAuthorization(@PathVariable String authorizationId,
                                                                    @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                    @RequestBody(required = false) Map<String, Object> request) {
        return service.revokeDownloadAuthorization(authorizationId, permissions, request == null ? Map.of() : request);
    }

    @PostMapping("/api/encrypted-documents/download-authorizations/explain")
    ResponseEntity<Map<String, Object>> explainDownloadAuthorization(@RequestBody Map<String, Object> request) {
        return service.explainDownloadAuthorization(request);
    }

    @PostMapping("/api/encrypted-documents/download-jobs")
    ResponseEntity<Map<String, Object>> createDecryptDownloadJob(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                 @RequestBody Map<String, Object> request) {
        return service.createDecryptDownloadJob(permissions, request);
    }

    @PostMapping("/api/encrypted-documents/download-jobs/{jobId}/deliver")
    ResponseEntity<Map<String, Object>> deliverDecryptDownloadJob(@PathVariable String jobId,
                                                                   @RequestBody(required = false) Map<String, Object> request) {
        return service.deliverDecryptDownloadJob(jobId, request == null ? Map.of() : request);
    }

    @PostMapping("/api/encrypted-documents/download-jobs/{jobId}/expire")
    ResponseEntity<Map<String, Object>> expireDecryptDownloadJob(@PathVariable String jobId,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return service.expireDecryptDownloadJob(jobId, request == null ? Map.of() : request);
    }

    @PostMapping("/api/contracts/{contractId}/performance-records")
    ResponseEntity<Map<String, Object>> createPerformanceRecord(@PathVariable String contractId,
                                                                @RequestBody Map<String, Object> request) {
        return service.createPerformanceRecord(contractId, request);
    }

    @GetMapping("/api/contracts/{contractId}/performance-overview")
    Map<String, Object> performanceOverview(@PathVariable String contractId) {
        return service.performanceOverview(contractId);
    }

    @PostMapping("/api/contracts/{contractId}/performance-nodes")
    ResponseEntity<Map<String, Object>> createPerformanceNode(@PathVariable String contractId,
                                                              @RequestBody Map<String, Object> request) {
        return service.createPerformanceNode(contractId, request);
    }

    @PatchMapping("/api/contracts/{contractId}/performance-nodes/{nodeId}")
    ResponseEntity<Map<String, Object>> updatePerformanceNode(@PathVariable String contractId,
                                                              @PathVariable String nodeId,
                                                              @RequestBody Map<String, Object> request) {
        return service.updatePerformanceNode(contractId, nodeId, request);
    }

    @GetMapping("/api/encrypted-documents/audit-events")
    Map<String, Object> encryptedDocumentAuditEvents(@RequestParam(required = false) String document_asset_id,
                                                     @RequestParam(required = false) String contract_id,
                                                     @RequestParam(required = false) String event_type) {
        return service.encryptedDocumentAuditEvents(document_asset_id, contract_id, event_type);
    }
}

@org.springframework.stereotype.Service
class CoreChainService {

    private final Map<String, ContractState> contracts = new ConcurrentHashMap<>();
    private final Map<String, DocumentAssetState> documentAssets = new ConcurrentHashMap<>();
    private final Map<String, DocumentVersionState> documentVersions = new ConcurrentHashMap<>();
    private final Map<String, ProcessDefinitionState> processDefinitions = new ConcurrentHashMap<>();
    private final Map<String, ProcessVersionState> processVersions = new ConcurrentHashMap<>();
    private final Map<String, WorkflowProcessState> workflowProcesses = new ConcurrentHashMap<>();
    private final Map<String, ApprovalTaskState> approvalTasks = new ConcurrentHashMap<>();
    private final Map<String, ProcessState> processes = new ConcurrentHashMap<>();
    private final Map<String, String> oaProcessIndex = new ConcurrentHashMap<>();
    private final Map<String, CompensationTaskState> compensationTasks = new ConcurrentHashMap<>();
    private final Map<String, SignatureRequestState> signatureRequests = new ConcurrentHashMap<>();
    private final Map<String, String> signatureIdempotencyIndex = new ConcurrentHashMap<>();
    private final Map<String, SignatureSessionState> signatureSessions = new ConcurrentHashMap<>();
    private final Map<String, SignatureResultState> signatureResults = new ConcurrentHashMap<>();
    private final Map<String, PaperRecordState> paperRecords = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> signatureSummaries = new ConcurrentHashMap<>();
    private final Map<String, EncryptionSecurityBindingState> encryptionSecurityBindings = new ConcurrentHashMap<>();
    private final Map<String, String> encryptionBindingByDocumentAsset = new ConcurrentHashMap<>();
    private final Map<String, EncryptionCheckInState> encryptionCheckIns = new ConcurrentHashMap<>();
    private final Map<String, String> encryptionCheckInByVersionTrigger = new ConcurrentHashMap<>();
    private final Map<String, DecryptAccessState> decryptAccesses = new ConcurrentHashMap<>();
    private final Map<String, DownloadAuthorizationState> downloadAuthorizations = new ConcurrentHashMap<>();
    private final Map<String, DecryptDownloadJobState> decryptDownloadJobs = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> encryptedDocumentAuditRecords = new ArrayList<>();
    private final Map<String, PerformanceRecordState> performanceRecords = new ConcurrentHashMap<>();
    private final Map<String, String> performanceRecordByContract = new ConcurrentHashMap<>();
    private final Map<String, PerformanceNodeState> performanceNodes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> performanceSummaries = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> lifecycleSummaries = new ConcurrentHashMap<>();

    Map<String, Object> createContract(Map<String, Object> request) {
        String contractId = "ctr-" + UUID.randomUUID();
        String contractNo = "CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ContractState contract = new ContractState(contractId, contractNo, text(request, "contract_name", "未命名合同"), "DRAFT",
                text(request, "owner_org_unit_id", null), text(request, "owner_user_id", null), text(request, "amount", null),
                text(request, "currency", null), null, new ArrayList<>(), null, null, new ArrayList<>());
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
                text(request, "currency", contract.currency()), contract.currentDocument(), copyDocuments(contract.attachments()),
                contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event("CONTRACT_UPDATED", contractId, text(request, "trace_id", null)));
        contracts.put(contractId, updated);
        return ResponseEntity.ok(contractBody(updated));
    }

    ResponseEntity<Map<String, Object>> createDocumentAssetResponse(Map<String, Object> request) {
        if (bool(request, "simulate_document_write_failure", false)) {
            Map<String, Object> body = error("DOCUMENT_WRITE_FAILED", "文档写入失败，等待重试");
            body.put("recovery_action", "RETRY_DOCUMENT_WRITE");
            body.put("trace_id", text(request, "trace_id", null));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(createDocumentAsset(request));
    }

    Map<String, Object> createDocumentAsset(Map<String, Object> request) {
        String contractId = text(request, "owner_id", null);
        ContractState contract = requireContract(contractId);
        String documentAssetId = "doc-asset-" + UUID.randomUUID();
        String documentVersionId = "doc-ver-" + UUID.randomUUID();
        List<Map<String, Object>> auditRecords = new ArrayList<>();
        auditRecords.add(event("DOCUMENT_ASSET_CREATED", documentAssetId, text(request, "trace_id", null)));
        String documentRole = normalizeDocumentRole(text(request, "document_role", text(request, "document_kind", "MAIN_BODY")));
        DocumentAssetState asset = new DocumentAssetState(documentAssetId, documentVersionId, text(request, "owner_type", "CONTRACT"), contractId,
                documentRole,
                text(request, "document_title", text(request, "document_name", null)), "FIRST_VERSION_WRITTEN", "PENDING", "NOT_GENERATED", 1, auditRecords);
        documentAssets.put(documentAssetId, asset);
        documentVersions.put(documentVersionId, new DocumentVersionState(documentVersionId, documentAssetId, 1, null, null,
                text(request, "version_label", "V1"), "首版写入", "ACTIVE", text(request, "file_upload_token", null), text(request, "source_channel", "MANUAL_UPLOAD")));

        bindContractDocument(contract, asset, text(request, "trace_id", null));
        acceptEncryptionCheckIn(documentAssetId, documentVersionId, "NEW_VERSION", request, false);
        DocumentAssetState currentAsset = requireDocumentAsset(documentAssetId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contractId);
        body.putAll(documentAssetBody(currentAsset));
        body.put("document_version_id", currentAsset.currentVersionId());
        return body;
    }

    Map<String, Object> appendDocumentVersion(String documentAssetId, Map<String, Object> request) {
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        int versionNo = asset.latestVersionNo() + 1;
        String versionId = "doc-ver-" + UUID.randomUUID();
        DocumentVersionState version = new DocumentVersionState(versionId, documentAssetId, versionNo, asset.currentVersionId(), text(request, "base_version_id", null),
                text(request, "version_label", "V" + versionNo), text(request, "change_reason", null), "ACTIVE",
                text(request, "file_upload_token", null), text(request, "source_channel", "MANUAL_UPLOAD"));
        documentVersions.put(versionId, version);

        DocumentAssetState updated = new DocumentAssetState(asset.documentAssetId(), versionId, asset.ownerType(), asset.ownerId(), asset.documentRole(), asset.documentTitle(),
                asset.documentStatus(), "PENDING", asset.previewStatus(), versionNo, copyEvents(asset.auditRecords()));
        updated.auditRecords().add(event("DOCUMENT_VERSION_APPENDED", versionId, text(request, "trace_id", null)));
        documentAssets.put(documentAssetId, updated);
        refreshContractDocumentRef(updated, text(request, "trace_id", null));
        acceptEncryptionCheckIn(documentAssetId, versionId, "NEW_VERSION", request, false);
        return documentVersionBody(version);
    }

    Map<String, Object> documentAsset(String documentAssetId) {
        return documentAssetBody(requireDocumentAsset(documentAssetId));
    }

    Map<String, Object> documentAssets(String ownerType, String ownerId) {
        List<Map<String, Object>> items = documentAssets.values().stream()
                .filter(asset -> ownerType == null || ownerType.equals(asset.ownerType()))
                .filter(asset -> ownerId == null || ownerId.equals(asset.ownerId()))
                .map(this::documentAssetBody)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    Map<String, Object> documentVersions(String documentAssetId) {
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        List<Map<String, Object>> items = documentVersions.values().stream()
                .filter(version -> documentAssetId.equals(version.documentAssetId()))
                .sorted((left, right) -> Integer.compare(left.versionNo(), right.versionNo()))
                .map(this::documentVersionBody)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_asset_id", asset.documentAssetId());
        body.put("current_version_id", asset.currentVersionId());
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    Map<String, Object> documentVersion(String documentVersionId) {
        return documentVersionBody(requireDocumentVersion(documentVersionId));
    }

    Map<String, Object> activateDocumentVersion(String documentVersionId, Map<String, Object> request) {
        DocumentVersionState version = requireDocumentVersion(documentVersionId);
        DocumentAssetState asset = requireDocumentAsset(version.documentAssetId());
        DocumentAssetState updated = new DocumentAssetState(asset.documentAssetId(), documentVersionId, asset.ownerType(), asset.ownerId(), asset.documentRole(), asset.documentTitle(),
                asset.documentStatus(), asset.encryptionStatus(), asset.previewStatus(), asset.latestVersionNo(), copyEvents(asset.auditRecords()));
        updated.auditRecords().add(event("DOCUMENT_VERSION_ACTIVATED", documentVersionId, text(request, "trace_id", null)));
        documentAssets.put(asset.documentAssetId(), updated);
        refreshContractDocumentRef(updated, text(request, "trace_id", null));
        return documentAssetBody(updated);
    }

    Map<String, Object> createProcessDefinition(Map<String, Object> request) {
        String definitionId = "wf-def-" + UUID.randomUUID();
        String draftVersionId = "wf-ver-" + UUID.randomUUID();
        Map<String, Object> draftSnapshot = map(request.get("definition_payload"));
        ProcessVersionState draftVersion = new ProcessVersionState(draftVersionId, definitionId, 1, "DRAFT", draftSnapshot,
                null, null, text(request, "operator_user_id", null), null);
        processVersions.put(draftVersionId, draftVersion);

        ProcessDefinitionState definition = new ProcessDefinitionState(definitionId, text(request, "process_code", null),
                text(request, "process_name", null), text(request, "business_type", null), text(request, "approval_mode", "CMP"),
                "DRAFTING", draftVersionId, null, bool(request, "organization_binding_required", true), copyEvents(List.of()));
        processDefinitions.put(definitionId, definition);
        return processDefinitionBody(definition);
    }

    ResponseEntity<Map<String, Object>> publishProcessDefinition(String definitionId, Map<String, Object> request) {
        ProcessDefinitionState definition = requireProcessDefinition(definitionId);
        ProcessVersionState draft = requireProcessVersion(definition.currentDraftVersionId());
        List<Map<String, Object>> validationErrors = missingApprovalNodeBindings(draft.versionSnapshot());
        if (definition.organizationBindingRequired() && !validationErrors.isEmpty()) {
            String errorCode = validationErrors.stream()
                    .anyMatch(error -> "ORG_NODE_RESOLUTION_FAILED".equals(error.get("error_code")))
                    ? "ORG_NODE_RESOLUTION_FAILED" : "WORKFLOW_NODE_BINDING_REQUIRED";
            String message = "ORG_NODE_RESOLUTION_FAILED".equals(errorCode) ? "组织节点解析失败" : "审批节点必须绑定组织架构对象";
            Map<String, Object> body = error(errorCode, message);
            body.put("validation_errors", validationErrors);
            return ResponseEntity.unprocessableEntity().body(body);
        }

        ProcessVersionState publishedVersion = new ProcessVersionState(draft.versionId(), definition.definitionId(), draft.versionNo(), "PUBLISHED",
                draft.versionSnapshot(), Instant.now().toString(), text(request, "operator_user_id", draft.publishedBy()),
                draft.publishedBy(), text(request, "version_note", null));
        processVersions.put(publishedVersion.versionId(), publishedVersion);
        ProcessDefinitionState updated = new ProcessDefinitionState(definition.definitionId(), definition.processCode(), definition.processName(),
                definition.businessType(), definition.approvalMode(), "PUBLISHED", definition.currentDraftVersionId(), publishedVersion.versionId(),
                definition.organizationBindingRequired(), copyEvents(definition.auditRecords()));
        updated.auditRecords().add(event("PROCESS_VERSION_PUBLISHED", publishedVersion.versionId(), text(request, "trace_id", null)));
        processDefinitions.put(definitionId, updated);
        return ResponseEntity.ok(processDefinitionBody(updated));
    }

    Map<String, Object> disableProcessDefinition(String definitionId, Map<String, Object> request) {
        ProcessDefinitionState definition = requireProcessDefinition(definitionId);
        ProcessVersionState latest = definition.latestPublishedVersionId() == null ? null : requireProcessVersion(definition.latestPublishedVersionId());
        if (latest != null) {
            processVersions.put(latest.versionId(), new ProcessVersionState(latest.versionId(), latest.definitionId(), latest.versionNo(), "DISABLED",
                    latest.versionSnapshot(), latest.publishedAt(), latest.publishedBy(), latest.createdBy(), latest.versionNote()));
        }
        ProcessDefinitionState updated = new ProcessDefinitionState(definition.definitionId(), definition.processCode(), definition.processName(),
                definition.businessType(), definition.approvalMode(), "DISABLED", definition.currentDraftVersionId(), definition.latestPublishedVersionId(),
                definition.organizationBindingRequired(), copyEvents(definition.auditRecords()));
        updated.auditRecords().add(event("PROCESS_VERSION_DISABLED", definition.latestPublishedVersionId(), text(request, "trace_id", null)));
        processDefinitions.put(definitionId, updated);
        return processDefinitionBody(updated);
    }

    Map<String, Object> startApprovalProcess(Map<String, Object> request) {
        ProcessVersionState version = requireProcessVersion(text(request, "version_id", null));
        if (!"PUBLISHED".equals(version.versionStatus())) {
            throw new IllegalArgumentException("只能基于已发布版本发起审批");
        }
        ProcessDefinitionState definition = requireProcessDefinition(version.definitionId());
        Map<String, Object> node = firstApprovalNode(version.versionSnapshot());
        String processId = "wf-proc-" + UUID.randomUUID();
        List<String> taskIds = new ArrayList<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        WorkflowProcessState process = new WorkflowProcessState(processId, definition.definitionId(), version.versionId(),
                text(request, "contract_id", null), definition.approvalMode(), "RUNNING", text(node, "node_key", null),
                text(request, "starter_user_id", null), Instant.now().toString(), null, taskIds, actions);
        workflowProcesses.put(processId, process);

        List<Map<String, Object>> bindings = bindings(node);
        String participantMode = text(node, "participant_mode", "SINGLE");
        int taskCount = "SINGLE".equals(participantMode) ? 1 : bindings.size();
        for (int index = 0; index < taskCount; index++) {
            ApprovalTaskState task = createApprovalTask(process, node, bindings.get(index), participantMode, index);
            approvalTasks.put(task.taskId(), task);
            taskIds.add(task.taskId());
        }
        return workflowProcessBody(process, pendingTasks(processId));
    }

    Map<String, Object> approvalTasks(String processId, String taskStatus) {
        List<Map<String, Object>> items = approvalTasks.values().stream()
                .filter(task -> processId == null || processId.equals(task.processId()))
                .filter(task -> taskStatus == null || taskStatus.equals(task.taskStatus()))
                .map(this::approvalTaskBody)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    Map<String, Object> submitApprovalAction(String processId, Map<String, Object> request) {
        WorkflowProcessState process = requireWorkflowProcess(processId);
        String taskId = text(request, "task_id", null);
        ApprovalTaskState task = requireApprovalTask(taskId);
        String actionType = text(request, "action_type", "APPROVE");
        String actionId = "wf-action-" + UUID.randomUUID();
        Map<String, Object> action = approvalAction(actionId, processId, taskId, actionType, text(request, "operator_user_id", null), text(request, "comment", null));
        process.approvalActions().add(action);

        String closedStatus = switch (actionType) {
            case "REJECT" -> "REJECTED";
            case "TERMINATE" -> "CANCELED";
            default -> "COMPLETED";
        };
        approvalTasks.put(taskId, new ApprovalTaskState(task.taskId(), task.processId(), task.nodeKey(), task.taskType(), closedStatus,
                task.assigneeUserId(), task.assigneeOrgUnitId(), task.candidateList(), task.resolverSnapshot(), task.sequenceIndex(),
                task.participantMode(), Instant.now().toString(), task.taskCenterRef()));

        List<Map<String, Object>> nextTasks = new ArrayList<>();
        String nextStatus = process.instanceStatus();
        if ("REJECT".equals(actionType)) {
            nextStatus = "REJECTED";
            cancelPendingTasks(processId);
        } else if ("TERMINATE".equals(actionType)) {
            nextStatus = "TERMINATED";
            cancelPendingTasks(processId);
        } else if ("SINGLE".equals(task.participantMode()) && hasNextSerialTask(process, task)) {
            ProcessVersionState version = requireProcessVersion(process.versionId());
            Map<String, Object> node = firstApprovalNode(version.versionSnapshot());
            Map<String, Object> binding = bindings(node).get(task.sequenceIndex() + 1);
            ApprovalTaskState nextTask = createApprovalTask(process, node, binding, "SINGLE", task.sequenceIndex() + 1);
            approvalTasks.put(nextTask.taskId(), nextTask);
            process.taskIds().add(nextTask.taskId());
            nextTasks.add(approvalTaskBody(nextTask));
        } else if (pendingTasks(processId).isEmpty()) {
            nextStatus = "COMPLETED";
        }

        WorkflowProcessState updated = new WorkflowProcessState(process.processId(), process.definitionId(), process.versionId(), process.contractId(),
                process.approvalMode(), nextStatus, process.currentNodeKey(), process.starterUserId(), process.startedAt(),
                isTerminal(nextStatus) ? Instant.now().toString() : null, process.taskIds(), process.approvalActions());
        workflowProcesses.put(processId, updated);
        Map<String, Object> body = workflowProcessBody(updated, nextTasks);
        body.put("approval_action_id", actionId);
        body.put("action_result", "ACCEPTED");
        body.put("next_tasks", nextTasks);
        body.put("approval_actions", updated.approvalActions());
        return body;
    }

    Map<String, Object> startProcess(Map<String, Object> request) {
        Map<String, Object> legacyRequest = new LinkedHashMap<>(request);
        legacyRequest.put("acceptance_strategy", "LEGACY_WORKFLOW");
        return startContractApproval(text(request, "contract_id", null), legacyRequest);
    }

    ResponseEntity<Map<String, Object>> startContractApprovalResponse(String contractId, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        String documentAssetId = text(request, "document_asset_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        if (contract.currentDocument() != null
                && documentAssetId.equals(contract.currentDocument().documentAssetId())
                && !documentVersionId.equals(contract.currentDocument().documentVersionId())) {
            Map<String, Object> body = error("DOCUMENT_VERSION_STALE", "审批发起使用的文档版本不是当前有效版本");
            body.put("current_document_version_id", contract.currentDocument().documentVersionId());
            body.put("requested_document_version_id", documentVersionId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(startContractApproval(contractId, request));
    }

    Map<String, Object> startContractApproval(String contractId, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        String documentAssetId = text(request, "document_asset_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        if (contract.currentDocument() == null
                || !documentAssetId.equals(contract.currentDocument().documentAssetId())
                || !documentVersionId.equals(contract.currentDocument().documentVersionId())) {
            throw new IllegalArgumentException("审批发起必须消费合同当前文档引用");
        }

        String processId = "proc-" + UUID.randomUUID();
        String approvalMode = text(request, "acceptance_strategy", "OA");
        String processStatus = switch (approvalMode) {
            case "CMP_WORKFLOW" -> "RUNNING";
            case "LEGACY_WORKFLOW" -> "STARTED";
            default -> "WAITING_CALLBACK";
        };
        String oaInstanceId = "OA".equals(approvalMode) ? "oa-inst-" + UUID.randomUUID() : null;
        Map<String, Object> approvalSummary = approvalSummary(processId, processStatus, null, approvalMode, "HEALTHY");
        copyApprovalFlowRefs(approvalSummary, request);
        ProcessState process = new ProcessState(processId, contractId, documentAssetId, documentVersionId, processStatus, approvalSummary,
                approvalMode, oaInstanceId, 0, new ArrayList<>(), bool(request, "simulate_contract_writeback_failure", false));
        processes.put(processId, process);
        if (oaInstanceId != null) {
            oaProcessIndex.put(oaInstanceId, processId);
        }

        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), "UNDER_APPROVAL",
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                copyDocuments(contract.attachments()), approvalSummary, processId, copyEvents(contract.events()));
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
        Map<String, Object> approvalSummary = approvalSummary(processId, processStatus, result, process.approvalMode(), "HEALTHY");
        copyApprovalFlowRefs(approvalSummary, process.approvalSummary());
        ProcessState updatedProcess = new ProcessState(processId, process.contractId(), process.documentAssetId(), process.documentVersionId(), processStatus, approvalSummary,
                process.approvalMode(), process.oaInstanceId(), process.lastEventSequence(), copyCallbackEventIds(process.callbackEventIds()), process.simulateContractWritebackFailure());
        processes.put(processId, updatedProcess);

        ContractState updatedContract = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contractStatus,
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                copyDocuments(contract.attachments()), approvalSummary, processId, copyEvents(contract.events()));
        updatedContract.events().add(event(eventType(processStatus), processId, text(request, "trace_id", null)));
        contracts.put(contract.contractId(), updatedContract);
        return processBody(updatedProcess);
    }

    Map<String, Object> approvalSummaryByProcess(String processId) {
        return new LinkedHashMap<>(requireProcess(processId).approvalSummary());
    }

    Map<String, Object> acceptOaCallback(Map<String, Object> request) {
        ProcessState process = requireProcess(oaProcessIndex.get(text(request, "oa_instance_id", null)));
        String callbackEventId = text(request, "callback_event_id", null);
        int eventSequence = intValue(request, "event_sequence", 0);
        if (process.callbackEventIds().contains(callbackEventId)) {
            return callbackBody("DUPLICATE_IGNORED", process.approvalSummary());
        }
        if (eventSequence <= process.lastEventSequence()) {
            return callbackBody("OUT_OF_ORDER_IGNORED", process.approvalSummary());
        }

        String result = switch (text(request, "oa_status", "APPROVING")) {
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "TERMINATED", "CANCELED" -> "TERMINATED";
            default -> null;
        };
        String processStatus = result == null ? "WAITING_CALLBACK" : switch (result) {
            case "APPROVED" -> "COMPLETED";
            case "REJECTED" -> "REJECTED";
            default -> "TERMINATED";
        };
        List<String> callbackEventIds = copyCallbackEventIds(process.callbackEventIds());
        callbackEventIds.add(callbackEventId);
        String compensationStatus = process.simulateContractWritebackFailure() && result != null ? "SUMMARY_COMPENSATING" : "HEALTHY";
        Map<String, Object> approvalSummary = approvalSummary(process.processId(), processStatus, result, process.approvalMode(), compensationStatus);
        copyApprovalFlowRefs(approvalSummary, process.approvalSummary());
        ProcessState updatedProcess = new ProcessState(process.processId(), process.contractId(), process.documentAssetId(), process.documentVersionId(), processStatus,
                approvalSummary, process.approvalMode(), process.oaInstanceId(), eventSequence, callbackEventIds, process.simulateContractWritebackFailure());
        processes.put(process.processId(), updatedProcess);

        if (process.simulateContractWritebackFailure() && result != null) {
            createCompensationTask(updatedProcess, text(request, "trace_id", null));
            return callbackBody("WRITEBACK_COMPENSATING", approvalSummary);
        }
        if (result != null) {
            writeBackContractApproval(updatedProcess, processStatus, text(request, "trace_id", null));
        }
        return callbackBody("ACCEPTED", approvalSummary);
    }

    Map<String, Object> compensationTasks(String contractId) {
        List<Map<String, Object>> items = compensationTasks.values().stream()
                .filter(task -> contractId == null || contractId.equals(task.contractId()))
                .map(this::compensationTaskBody)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    ResponseEntity<Map<String, Object>> applySignature(String contractId, String permissions, String idempotencyKey, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("SIGNATURE_APPLY")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("PERMISSION_DENIED", "缺少签章发起权限"));
        }
        if (idempotencyKey != null && signatureIdempotencyIndex.containsKey(idempotencyKey)) {
            Map<String, Object> body = signatureRequestBody(requireSignatureRequest(signatureIdempotencyIndex.get(idempotencyKey)), true, true);
            return ResponseEntity.ok(body);
        }

        ContractState contract = requireContract(contractId);
        DocumentRef currentDocument = contract.currentDocument();
        String documentAssetId = text(request, "main_document_asset_id", null);
        String documentVersionId = text(request, "main_document_version_id", null);

        ResponseEntity<Map<String, Object>> rejection = signatureAdmissionRejection(contract, currentDocument, documentAssetId, documentVersionId);
        if (rejection != null) {
            return rejection;
        }

        String signatureRequestId = "sig-req-" + UUID.randomUUID();
        String fingerprint = contractId + ":" + documentAssetId + ":" + documentVersionId + ":"
                + text(request, "signature_mode", "ELECTRONIC") + ":" + text(request, "seal_scheme_id", "") + ":" + text(request, "sign_order_mode", "SERIAL");
        Map<String, Object> snapshot = signatureApplicationSnapshot(contract);
        Map<String, Object> binding = signatureInputBinding(contractId, signatureRequestId, documentAssetId, documentVersionId);
        List<Map<String, Object>> auditRecords = List.of(signatureAudit("SIGNATURE_APPLY_ADMITTED", contractId, signatureRequestId, text(request, "trace_id", null)));
        SignatureRequestState state = new SignatureRequestState(signatureRequestId, contractId, "ADMITTED", "READY",
                documentAssetId, documentVersionId, text(request, "signature_mode", "ELECTRONIC"), text(request, "seal_scheme_id", null),
                text(request, "sign_order_mode", "SERIAL"), fingerprint, idempotencyKey, snapshot, binding, auditRecords,
                Instant.now().toString(), text(request, "created_by", text(request, "operator_user_id", null)), null, null);
        signatureRequests.put(signatureRequestId, state);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            signatureIdempotencyIndex.put(idempotencyKey, signatureRequestId);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(signatureRequestBody(state, false, true));
    }

    Map<String, Object> signatureRequest(String signatureRequestId) {
        return signatureRequestBody(requireSignatureRequest(signatureRequestId), false, false);
    }

    Map<String, Object> createSignatureSession(String signatureRequestId, Map<String, Object> request) {
        SignatureRequestState signatureRequest = requireSignatureRequest(signatureRequestId);
        String sessionId = "sig-sess-" + UUID.randomUUID();
        List<Map<String, Object>> assignments = buildSignerAssignments(sessionId, signatureRequest, request);
        SignatureSessionState session = new SignatureSessionState(sessionId, signatureRequestId, signatureRequest.contractId(), "OPEN",
                1, text(request, "sign_order_mode", signatureRequest.signOrderMode()), 1, assignments.size(), 0,
                Instant.now().toString(), Instant.now().plusSeconds(intValue(request, "expires_in_seconds", 3600)).toString(), null,
                null, null, new ArrayList<>(), assignments);
        signatureSessions.put(sessionId, session);

        SignatureRequestState updatedRequest = new SignatureRequestState(signatureRequest.signatureRequestId(), signatureRequest.contractId(), "IN_PROGRESS",
                "IN_PROGRESS", signatureRequest.mainDocumentAssetId(), signatureRequest.mainDocumentVersionId(), signatureRequest.signatureMode(),
                signatureRequest.sealSchemeId(), session.signOrderMode(), signatureRequest.requestFingerprint(), signatureRequest.idempotencyKey(),
                signatureRequest.applicationSnapshot(), signatureRequest.inputDocumentBinding(), signatureRequest.auditRecords(), signatureRequest.createdAt(),
                signatureRequest.createdBy(), sessionId, signatureRequest.latestResultId());
        signatureRequests.put(signatureRequestId, updatedRequest);
        return signatureSessionBody(session, null);
    }

    ResponseEntity<Map<String, Object>> acceptSignatureCallback(String signatureSessionId, Map<String, Object> request) {
        SignatureSessionState session = requireSignatureSession(signatureSessionId);
        String externalEventId = text(request, "external_event_id", null);
        if (session.callbackEventIds().contains(externalEventId)) {
            Map<String, Object> body = signatureSessionBody(session, null);
            body.put("callback_result", "DUPLICATE_IGNORED");
            return ResponseEntity.ok(body);
        }
        if (!isSignatureSessionAdvanceable(session)) {
            Map<String, Object> body = signatureSessionBody(session, session.closeReason());
            body.put("callback_result", "SESSION_NOT_ADVANCEABLE");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        if ("ENGINE_ERROR".equals(text(request, "event_type", null))) {
            SignatureSessionState updated = sessionWithManualIntervention(session, "ENGINE_CALLBACK_EXCEPTION", externalEventId);
            signatureSessions.put(signatureSessionId, updated);
            Map<String, Object> body = signatureSessionBody(updated, "ENGINE_CALLBACK_EXCEPTION");
            body.put("callback_result", "MANUAL_INTERVENTION_REQUIRED");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }

        int eventSequence = intValue(request, "event_sequence", 0);
        String signerId = text(request, "signer_id", null);
        Map<String, Object> assignment = assignmentBySigner(session, signerId);
        int expectedStep = intValue(assignment, "sign_sequence_no", 0);
        if (eventSequence != session.currentSignStep() || expectedStep != session.currentSignStep()
                || !"READY".equals(text(assignment, "assignment_status", null))) {
            SignatureSessionState updated = sessionWithManualIntervention(session, "SIGNER_ORDER_VIOLATION", externalEventId);
            signatureSessions.put(signatureSessionId, updated);
            Map<String, Object> body = signatureSessionBody(updated, "SIGNER_ORDER_VIOLATION");
            body.put("callback_result", "OUT_OF_ORDER_REJECTED");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        List<Map<String, Object>> assignments = copyAssignmentList(session.assignments());
        for (Map<String, Object> item : assignments) {
            if (signerId.equals(text(item, "signer_id", null))) {
                item.put("assignment_status", "SIGNED");
                item.put("signed_at", Instant.now().toString());
            } else if (intValue(item, "sign_sequence_no", 0) == session.currentSignStep() + 1) {
                item.put("assignment_status", "READY");
            }
        }
        int completed = session.completedSignerCount() + 1;
        int pending = Math.max(0, session.pendingSignerCount() - 1);
        String status = pending == 0 ? "COMPLETED" : "PARTIALLY_SIGNED";
        List<String> callbacks = copyCallbackEventIds(session.callbackEventIds());
        callbacks.add(externalEventId);
        SignatureSessionState updated = new SignatureSessionState(session.signatureSessionId(), session.signatureRequestId(), session.contractId(), status,
                session.sessionRound(), session.signOrderMode(), pending == 0 ? session.currentSignStep() : session.currentSignStep() + 1,
                pending, completed, session.startedAt(), session.expiresAt(), pending == 0 ? Instant.now().toString() : null,
                null, externalEventId, callbacks, assignments);
        signatureSessions.put(signatureSessionId, updated);
        Map<String, Object> body = signatureSessionBody(updated, null);
        body.put("callback_result", "ACCEPTED");
        return ResponseEntity.ok(body);
    }

    Map<String, Object> expireSignatureSession(String signatureSessionId, Map<String, Object> request) {
        SignatureSessionState session = requireSignatureSession(signatureSessionId);
        SignatureSessionState updated = new SignatureSessionState(session.signatureSessionId(), session.signatureRequestId(), session.contractId(), "EXPIRED",
                session.sessionRound(), session.signOrderMode(), session.currentSignStep(), session.pendingSignerCount(), session.completedSignerCount(),
                session.startedAt(), session.expiresAt(), null, "SESSION_TIMEOUT", session.lastCallbackEventId(), session.callbackEventIds(),
                copyAssignmentList(session.assignments()));
        signatureSessions.put(signatureSessionId, updated);
        return signatureSessionBody(updated, "SESSION_TIMEOUT");
    }

    ResponseEntity<Map<String, Object>> writeBackSignatureResult(String signatureSessionId, Map<String, Object> request) {
        SignatureSessionState session = requireSignatureSession(signatureSessionId);
        if (!"COMPLETED".equals(session.sessionStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("SIGNATURE_SESSION_NOT_COMPLETED", "签章会话未完成"));
        }
        SignatureResultState existing = signatureResults.values().stream()
                .filter(result -> signatureSessionId.equals(result.signatureSessionId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(signatureResultBody(existing, null));
        }

        String resultId = "sig-result-" + UUID.randomUUID();
        Map<String, Object> signedBinding = createSignatureDocumentBinding(session.contractId(), session.signatureRequestId(), resultId,
                "SIGNED_MAIN", text(request, "signed_file_token", null), text(request, "trace_id", null));
        Map<String, Object> verificationBinding = createSignatureDocumentBinding(session.contractId(), session.signatureRequestId(), resultId,
                "VERIFICATION_ARTIFACT", text(request, "verification_file_token", null), text(request, "trace_id", null));
        String signedAssetId = text(signedBinding, "document_asset_id", null);
        String signedVersionId = text(signedBinding, "document_version_id", null);
        String resultStatus = bool(request, "simulate_contract_writeback_failure", false) ? "WRITEBACK_PARTIAL" : "WRITEBACK_COMPLETED";
        String contractWritebackStatus = bool(request, "simulate_contract_writeback_failure", false) ? "PENDING_COMPENSATION" : "COMPLETED";
        Map<String, Object> summary = signatureSummary(session.contractId(), "SIGNED", session.signatureRequestId(), resultId,
                signedAssetId, signedVersionId, "ELECTRONIC", text(request, "verification_status", "PASSED"), "SIGNATURE_RESULT");
        SignatureResultState result = new SignatureResultState(resultId, session.signatureRequestId(), signatureSessionId, session.contractId(), resultStatus,
                text(request, "verification_status", "PASSED"), "COMPLETED", contractWritebackStatus, signedAssetId, signedVersionId,
                text(verificationBinding, "document_asset_id", null), text(verificationBinding, "document_version_id", null),
                text(request, "external_result_ref", null), Instant.now().toString(), List.of(signedBinding, verificationBinding), summary);
        signatureResults.put(resultId, result);

        SignatureRequestState signatureRequest = requireSignatureRequest(session.signatureRequestId());
        signatureRequests.put(session.signatureRequestId(), new SignatureRequestState(signatureRequest.signatureRequestId(), signatureRequest.contractId(),
                contractWritebackStatus.equals("COMPLETED") ? "COMPLETED" : "IN_PROGRESS", contractWritebackStatus.equals("COMPLETED") ? "SIGNED" : "WRITEBACK_PARTIAL",
                signatureRequest.mainDocumentAssetId(), signatureRequest.mainDocumentVersionId(), signatureRequest.signatureMode(), signatureRequest.sealSchemeId(),
                signatureRequest.signOrderMode(), signatureRequest.requestFingerprint(), signatureRequest.idempotencyKey(), signatureRequest.applicationSnapshot(),
                signatureRequest.inputDocumentBinding(), signatureRequest.auditRecords(), signatureRequest.createdAt(), signatureRequest.createdBy(),
                session.signatureSessionId(), resultId));

        if (!contractWritebackStatus.equals("COMPLETED")) {
            Map<String, Object> body = signatureResultBody(result, compensationTask("ES_CONTRACT_WRITEBACK", session.contractId(), session.signatureSessionId(), text(request, "trace_id", null)));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }
        writeBackContractSignatureSummary(session.contractId(), summary, resultId, text(request, "trace_id", null));
        return ResponseEntity.ok(signatureResultBody(result, null));
    }

    Map<String, Object> rebuildSignatureSummary(String contractId, Map<String, Object> request) {
        Map<String, Object> summary = signatureResults.values().stream()
                .filter(result -> contractId.equals(result.contractId()))
                .filter(result -> "WRITEBACK_COMPLETED".equals(result.resultStatus()))
                .findFirst()
                .map(SignatureResultState::contractSignatureSummary)
                .orElseGet(() -> paperRecords.values().stream()
                        .filter(record -> contractId.equals(record.contractId()))
                        .filter(record -> "CONFIRMED".equals(record.recordStatus()))
                        .findFirst()
                        .map(record -> signatureSummary(contractId, "PAPER_RECORDED", record.signatureRequestId(), null,
                                record.paperDocumentAssetId(), record.paperDocumentVersionId(), "PAPER_RECORD", "MANUAL_CONFIRMED", "PAPER_RECORD"))
                        .orElseGet(() -> signatureSummary(contractId, "NOT_STARTED", null, null, null, null, null, null, "EMPTY")));
        summary.put("last_rebuild_at", Instant.now().toString());
        signatureSummaries.put(contractId, summary);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contractId);
        body.put("signature_summary", summary);
        return body;
    }

    ResponseEntity<Map<String, Object>> createPaperSignatureRecord(String contractId, Map<String, Object> request) {
        requireContract(contractId);
        boolean hasElectronicResult = signatureResults.values().stream()
                .anyMatch(result -> contractId.equals(result.contractId()) && "WRITEBACK_COMPLETED".equals(result.resultStatus()));
        if (hasElectronicResult && !bool(request, "allow_coexist_electronic", false)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PAPER_RECORD_CONFLICTS_WITH_ELECTRONIC_RESULT", "纸质备案与已完成电子签章结果冲突"));
        }
        String paperAssetId = text(request, "paper_document_asset_id", null);
        String paperVersionId = text(request, "paper_document_version_id", null);
        DocumentAssetState asset = requireDocumentAsset(paperAssetId);
        DocumentVersionState version = requireDocumentVersion(paperVersionId);
        if (!contractId.equals(asset.ownerId()) || !paperAssetId.equals(version.documentAssetId())) {
            return ResponseEntity.unprocessableEntity().body(error("PAPER_SCAN_DOCUMENT_INVALID", "纸质扫描件必须引用当前合同的文档中心版本"));
        }
        String paperRecordId = "paper-rec-" + UUID.randomUUID();
        String signatureRequestId = "sig-req-paper-" + UUID.randomUUID();
        Map<String, Object> binding = signatureDocumentBinding(contractId, signatureRequestId, null, paperRecordId,
                paperAssetId, paperVersionId, "PAPER_SCAN");
        Map<String, Object> summary = signatureSummary(contractId, "PAPER_RECORDED", signatureRequestId, null,
                paperAssetId, paperVersionId, "PAPER_RECORD", "MANUAL_CONFIRMED", "PAPER_RECORD");
        PaperRecordState paperRecord = new PaperRecordState(paperRecordId, contractId, signatureRequestId, "CONFIRMED",
                text(request, "recorded_sign_date", null), paperAssetId, paperVersionId, text(request, "confirmed_by", null),
                Instant.now().toString(), binding, summary);
        paperRecords.put(paperRecordId, paperRecord);
        signatureSummaries.put(contractId, summary);
        Map<String, Object> body = paperRecordBody(paperRecord, !hasElectronicResult || bool(request, "allow_coexist_electronic", false));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    ResponseEntity<Map<String, Object>> acceptEncryptionCheckInResponse(Map<String, Object> request) {
        String documentAssetId = text(request, "document_asset_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        String triggerType = text(request, "trigger_type", "NEW_VERSION");
        Map<String, Object> body = acceptEncryptionCheckIn(documentAssetId, documentVersionId, triggerType, request, true);
        return ResponseEntity.ok(body);
    }

    ResponseEntity<Map<String, Object>> requestDecryptAccess(String permissions, Map<String, Object> request) {
        String scene = text(request, "access_scene", null);
        String documentAssetId = text(request, "document_asset_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        String traceId = text(request, "trace_id", null);
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        EncryptionSecurityBindingState binding = requireEncryptionBindingByDocumentAsset(documentAssetId);
        if ("EXTERNAL_DOWNLOAD".equals(scene)) {
            Map<String, Object> body = error("PLAINTEXT_EXPORT_NOT_ALLOWED", "默认路径不允许平台外明文外放");
            Map<String, Object> audit = encryptionAudit("DECRYPT_ACCESS_DENIED", "REJECTED", binding.securityBindingId(), documentAssetId, documentVersionId,
                    asset.ownerId(), text(request, "access_subject_type", "USER"), text(request, "access_subject_id", null), text(request, "actor_department_id", null), null, traceId);
            body.put("audit_event", audit);
            asset.auditRecords().add(audit);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        if (!allowedAccessScene(scene)) {
            Map<String, Object> audit = encryptionAudit("DECRYPT_ACCESS_DENIED", "REJECTED", binding.securityBindingId(), documentAssetId, documentVersionId,
                    asset.ownerId(), text(request, "access_subject_type", "USER"), text(request, "access_subject_id", null), text(request, "actor_department_id", null), null, traceId);
            Map<String, Object> body = error("CONTROLLED_ACCESS_SCENE_DENIED", "受控读取场景不在允许白名单内");
            body.put("audit_event", audit);
            asset.auditRecords().add(audit);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        if (permissions == null || !permissions.contains("CONTRACT_VIEW") || !"ENCRYPTED".equals(binding.encryptionStatus())
                || !documentVersionId.equals(binding.currentVersionId())) {
            Map<String, Object> audit = encryptionAudit("DECRYPT_ACCESS_DENIED", "REJECTED", binding.securityBindingId(), documentAssetId, documentVersionId,
                    asset.ownerId(), text(request, "access_subject_type", "USER"), text(request, "access_subject_id", null), text(request, "actor_department_id", null), null, traceId);
            Map<String, Object> body = error("CONTROLLED_ACCESS_DENIED", "受控读取访问被拒绝");
            body.put("audit_event", audit);
            asset.auditRecords().add(audit);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        String decryptAccessId = "ed-access-" + UUID.randomUUID();
        String ticket = "ed-ticket-" + UUID.randomUUID();
        String mode = consumptionMode(scene);
        String expiresAt = Instant.now().plusSeconds(intValue(request, "ttl_seconds", 300)).toString();
        DecryptAccessState access = new DecryptAccessState(decryptAccessId, binding.securityBindingId(), documentAssetId, documentVersionId,
                asset.ownerId(), scene, text(request, "access_subject_type", "USER"), text(request, "access_subject_id", null),
                text(request, "actor_department_id", null), "APPROVED", "POLICY_ALLOWED", ticket, expiresAt, mode,
                text(request, "trace_id", null), null);
        decryptAccesses.put(decryptAccessId, access);
        Map<String, Object> audit = encryptionAudit("DECRYPT_ACCESS_APPROVED", "SUCCESS", binding.securityBindingId(), documentAssetId, documentVersionId,
                asset.ownerId(), access.accessSubjectType(), access.accessSubjectId(), access.actorDepartmentId(), decryptAccessId, traceId);
        asset.auditRecords().add(audit);
        return ResponseEntity.status(HttpStatus.CREATED).body(decryptAccessBody(access, audit));
    }

    Map<String, Object> expireDecryptAccess(String decryptAccessId, Map<String, Object> request) {
        DecryptAccessState access = requireDecryptAccess(decryptAccessId);
        DecryptAccessState updated = updateDecryptAccessResult(access, "EXPIRED", "TICKET_EXPIRED");
        Map<String, Object> audit = auditDecryptAccessLifecycle(updated, "DECRYPT_ACCESS_EXPIRED", text(request, "trace_id", null));
        return decryptAccessBody(updated, audit);
    }

    Map<String, Object> revokeDecryptAccess(String decryptAccessId, Map<String, Object> request) {
        DecryptAccessState access = requireDecryptAccess(decryptAccessId);
        DecryptAccessState updated = updateDecryptAccessResult(access, "REVOKED", "TICKET_REVOKED");
        Map<String, Object> audit = auditDecryptAccessLifecycle(updated, "DECRYPT_ACCESS_REVOKED", text(request, "trace_id", null));
        return decryptAccessBody(updated, audit);
    }

    ResponseEntity<Map<String, Object>> consumeDecryptAccess(String decryptAccessId, Map<String, Object> request) {
        DecryptAccessState access = requireDecryptAccess(decryptAccessId);
        if (!access.accessTicket().equals(text(request, "access_ticket", null))) {
            Map<String, Object> audit = encryptionAudit("DECRYPT_ACCESS_DENIED", "REJECTED", access.securityBindingId(), access.documentAssetId(), access.documentVersionId(),
                    access.contractId(), access.accessSubjectType(), access.accessSubjectId(), access.actorDepartmentId(), access.decryptAccessId(), text(request, "trace_id", null));
            requireDocumentAsset(access.documentAssetId()).auditRecords().add(audit);
            Map<String, Object> body = error("ACCESS_TICKET_INVALID", "访问票据无效");
            body.put("audit_event", audit);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        if ("EXPIRED".equals(access.accessResult())) {
            return ResponseEntity.status(HttpStatus.GONE).body(error("ACCESS_TICKET_EXPIRED", "访问票据已过期"));
        }
        if ("REVOKED".equals(access.accessResult())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("ACCESS_TICKET_REVOKED", "访问票据已撤销"));
        }
        if (Instant.now().isAfter(Instant.parse(access.ticketExpiresAt()))) {
            DecryptAccessState expired = updateDecryptAccessResult(access, "EXPIRED", "TICKET_EXPIRED");
            Map<String, Object> audit = auditDecryptAccessLifecycle(expired, "DECRYPT_ACCESS_EXPIRED", text(request, "trace_id", null));
            Map<String, Object> body = error("ACCESS_TICKET_EXPIRED", "访问票据已过期");
            body.put("audit_event", audit);
            return ResponseEntity.status(HttpStatus.GONE).body(body);
        }
        DecryptAccessState updated = new DecryptAccessState(access.decryptAccessId(), access.securityBindingId(), access.documentAssetId(),
                access.documentVersionId(), access.contractId(), access.accessScene(), access.accessSubjectType(), access.accessSubjectId(),
                access.actorDepartmentId(), access.accessResult(), access.decisionReasonCode(), access.accessTicket(), access.ticketExpiresAt(),
                access.consumptionMode(), access.traceId(), Instant.now().toString());
        decryptAccesses.put(decryptAccessId, updated);
        Map<String, Object> body = decryptAccessBody(updated, auditDecryptAccessLifecycle(updated, "DECRYPT_ACCESS_CONSUMED", text(request, "trace_id", null)));
        body.put("consume_result", "CONSUMED");
        return ResponseEntity.ok(body);
    }

    ResponseEntity<Map<String, Object>> grantDownloadAuthorization(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("ENCRYPTED_DOCUMENT_AUTH_MANAGE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("PERMISSION_DENIED", "缺少解密下载授权管理权限"));
        }
        Instant now = Instant.now();
        Instant startAt = now.plusSeconds(intValue(request, "effective_start_offset_seconds", 0));
        Instant endAt = now.plusSeconds(intValue(request, "effective_end_offset_seconds", 3600));
        String status = now.isAfter(endAt) ? "EXPIRED" : "ACTIVE";
        String authorizationId = "ed-auth-" + UUID.randomUUID();
        DownloadAuthorizationState authorization = new DownloadAuthorizationState(authorizationId, text(request, "authorization_name", null), status,
                text(request, "subject_type", "USER"), text(request, "subject_id", null), text(request, "scope_type", "GLOBAL"),
                text(request, "scope_value", "*"), bool(request, "download_reason_required", true), startAt.toString(), endAt.toString(),
                intValue(request, "priority_no", 0), text(request, "granted_by", null), null, null, authorizationPolicySnapshot(request));
        downloadAuthorizations.put(authorizationId, authorization);
        Map<String, Object> audit = authorizationAudit(authorization, "DOWNLOAD_AUTH_GRANTED", "SUCCESS", text(request, "trace_id", null));
        appendAuthorizationAuditToScopedAssets(authorization, audit);
        return ResponseEntity.status(HttpStatus.CREATED).body(downloadAuthorizationBody(authorization, audit));
    }

    ResponseEntity<Map<String, Object>> revokeDownloadAuthorization(String authorizationId, String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("ENCRYPTED_DOCUMENT_AUTH_MANAGE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("PERMISSION_DENIED", "缺少解密下载授权管理权限"));
        }
        DownloadAuthorizationState authorization = requireDownloadAuthorization(authorizationId);
        DownloadAuthorizationState revoked = new DownloadAuthorizationState(authorization.authorizationId(), authorization.authorizationName(), "REVOKED",
                authorization.subjectType(), authorization.subjectId(), authorization.scopeType(), authorization.scopeValue(), authorization.downloadReasonRequired(),
                authorization.effectiveStartAt(), authorization.effectiveEndAt(), authorization.priorityNo(), authorization.grantedBy(),
                text(request, "revoked_by", null), Instant.now().toString(), authorization.policySnapshot());
        downloadAuthorizations.put(authorizationId, revoked);
        Map<String, Object> audit = authorizationAudit(revoked, "DOWNLOAD_AUTH_REVOKED", "SUCCESS", text(request, "trace_id", null));
        appendAuthorizationAuditToScopedAssets(revoked, audit);
        return ResponseEntity.ok(downloadAuthorizationBody(revoked, audit));
    }

    ResponseEntity<Map<String, Object>> explainDownloadAuthorization(Map<String, Object> request) {
        DocumentAssetState asset = requireDocumentAsset(text(request, "document_asset_id", null));
        AuthorizationDecision decision = evaluateDownloadAuthorization(request, asset, text(request, "trace_id", null), true);
        if (decision.authorization() == null) {
            Map<String, Object> body = error("DOWNLOAD_AUTHORIZATION_DENIED", "未命中有效解密下载授权");
            body.put("decision", "DENIED");
            body.put("reason_code", "DOWNLOAD_AUTHORIZATION_NOT_FOUND");
            body.put("audit_event", decision.auditEvent());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        return ResponseEntity.ok(downloadAuthorizationDecisionBody(decision));
    }

    ResponseEntity<Map<String, Object>> createDecryptDownloadJob(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("CONTRACT_VIEW")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("PERMISSION_DENIED", "缺少合同查看权限"));
        }
        String documentAssetId = text(request, "document_asset_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        EncryptionSecurityBindingState binding = requireEncryptionBindingByDocumentAsset(documentAssetId);
        if (!"ENCRYPTED".equals(binding.encryptionStatus()) || !documentVersionId.equals(binding.currentVersionId())) {
            Map<String, Object> body = error("DOWNLOAD_DOCUMENT_NOT_READY", "文档未处于可授权下载状态");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        AuthorizationDecision decision = evaluateDownloadAuthorization(request, asset, text(request, "trace_id", null), true);
        if (decision.authorization() == null) {
            Map<String, Object> body = error("DOWNLOAD_AUTHORIZATION_DENIED", "未命中有效解密下载授权");
            body.put("audit_event", decision.auditEvent());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        String jobId = "ed-download-job-" + UUID.randomUUID();
        boolean failed = bool(request, "simulate_export_failure", false);
        String status = failed ? "FAILED" : "READY";
        String token = "ed-download-token-" + UUID.randomUUID();
        String artifactRef = "ed-package-" + UUID.randomUUID();
        DecryptDownloadJobState job = new DecryptDownloadJobState(jobId, binding.securityBindingId(), decision.authorization().authorizationId(),
                documentAssetId, documentVersionId, asset.ownerId(), text(request, "requested_by", null), text(request, "requested_department_id", null),
                text(request, "download_reason", null), text(request, "request_idempotency_key", null), status, decision.snapshot(), artifactRef,
                "明文导出-" + documentVersionId + ".bin", token, Instant.now().plusSeconds(intValue(request, "download_ttl_seconds", 600)).toString(),
                failed ? 1 : 0, "cmp-task-" + UUID.randomUUID(), failed ? "EXPORT_GENERATION_FAILED" : "EXPORT_READY",
                failed ? "导出生成失败，已创建补偿任务" : "明文导出包已生成", Instant.now().toString(), failed ? null : Instant.now().toString());
        decryptDownloadJobs.put(jobId, job);
        Map<String, Object> audit = encryptionAudit(failed ? "DOWNLOAD_EXPORT_FAILED" : "DOWNLOAD_READY", failed ? "FAILED" : "SUCCESS",
                binding.securityBindingId(), documentAssetId, documentVersionId, asset.ownerId(), "USER", job.requestedBy(), job.requestedDepartmentId(), jobId,
                text(request, "trace_id", null));
        asset.auditRecords().add(audit);
        Map<String, Object> body = decryptDownloadJobBody(job, audit);
        if (failed) {
            body.put("compensation_task", compensationTask("ED_DECRYPT_DOWNLOAD_EXPORT", asset.ownerId(), jobId, text(request, "trace_id", null)));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    ResponseEntity<Map<String, Object>> deliverDecryptDownloadJob(String jobId, Map<String, Object> request) {
        DecryptDownloadJobState job = requireDecryptDownloadJob(jobId);
        ResponseEntity<Map<String, Object>> conflict = rejectDownloadJobTransitionUnlessReady(job, "DELIVERED");
        if (conflict != null) {
            return conflict;
        }
        DecryptDownloadJobState delivered = updateDecryptDownloadJobStatus(job, "DELIVERED", "DOWNLOAD_DELIVERED", "明文导出已交付");
        Map<String, Object> audit = auditDownloadJob(delivered, "DOWNLOAD_DELIVERED", "SUCCESS", text(request, "trace_id", null));
        return ResponseEntity.ok(decryptDownloadJobBody(delivered, audit));
    }

    ResponseEntity<Map<String, Object>> expireDecryptDownloadJob(String jobId, Map<String, Object> request) {
        DecryptDownloadJobState job = requireDecryptDownloadJob(jobId);
        ResponseEntity<Map<String, Object>> conflict = rejectDownloadJobTransitionUnlessReady(job, "EXPIRED");
        if (conflict != null) {
            return conflict;
        }
        DecryptDownloadJobState expired = updateDecryptDownloadJobStatus(job, "EXPIRED", "DOWNLOAD_EXPIRED", "下载入口已过期回收");
        Map<String, Object> audit = auditDownloadJob(expired, "DOWNLOAD_EXPIRED", "SUCCESS", text(request, "trace_id", null));
        return ResponseEntity.ok(decryptDownloadJobBody(expired, audit));
    }

    Map<String, Object> encryptedDocumentAuditEvents(String documentAssetId, String contractId, String eventType) {
        DocumentAssetState scopedAsset = documentAssetId == null ? null : requireDocumentAsset(documentAssetId);
        List<Map<String, Object>> items = encryptedDocumentAuditRecords.stream()
                .filter(event -> documentAssetId == null
                        || documentAssetId.equals(text(event, "document_asset_id", null))
                        || scopedAsset.ownerId().equals(text(event, "contract_id", null)))
                .filter(event -> contractId == null || contractId.equals(text(event, "contract_id", null)))
                .filter(event -> eventType == null || eventType.equals(text(event, "event_type", null)))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
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
        body.put("attachment_summaries", contract.attachments().stream().map(this::documentBody).toList());
        body.put("approval_summary", contract.approvalSummary());
        body.put("lifecycle_summary", lifecycleSummaries.get(contractId));
        body.put("timeline_summary", contract.events());
        body.put("audit_record", contract.events());
        return body;
    }

    ResponseEntity<Map<String, Object>> createPerformanceRecord(String contractId, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        if (!"SIGNED".equals(contract.contractStatus()) && !"PERFORMED".equals(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("CONTRACT_NOT_EFFECTIVE_FOR_PERFORMANCE", "合同未签章或未生效，不能创建正式履约记录"));
        }
        String existingId = performanceRecordByContract.get(contractId);
        if (existingId != null) {
            return ResponseEntity.ok(performanceRecordBody(requirePerformanceRecord(existingId), contract.contractStatus()));
        }

        String recordId = "perf-rec-" + UUID.randomUUID();
        PerformanceRecordState record = new PerformanceRecordState(recordId, contractId, "IN_PROGRESS", 0, "LOW",
                text(request, "owner_user_id", contract.ownerUserId()), text(request, "owner_org_unit_id", contract.ownerOrgUnitId()),
                0, 0, null, "履约已启动", "PERFORMANCE_STARTED", Instant.now().toString(), Instant.now().toString());
        performanceRecords.put(recordId, record);
        performanceRecordByContract.put(contractId, recordId);
        Map<String, Object> summary = refreshPerformanceSummary(contractId, "PERFORMANCE_STARTED");
        lifecycleSummaries.put(contractId, lifecycleSummary(contractId, summary));
        appendContractEvent(contractId, "PERFORMANCE_STARTED", recordId, text(request, "trace_id", null), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(performanceRecordBody(record, contract.contractStatus()));
    }

    Map<String, Object> performanceOverview(String contractId) {
        requireContract(contractId);
        PerformanceRecordState record = performanceRecordForContract(contractId);
        List<Map<String, Object>> nodes = performanceNodes.values().stream()
                .filter(node -> contractId.equals(node.contractId()))
                .map(this::performanceNodeBody)
                .toList();
        List<Map<String, Object>> documentRefs = nodes.stream()
                .map(node -> map(node.get("document_ref")))
                .filter(ref -> !ref.isEmpty())
                .toList();
        Map<String, Object> summary = performanceSummaries.get(contractId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contractId);
        body.put("performance_record", record == null ? null : performanceRecordBody(record, requireContract(contractId).contractStatus()));
        body.put("nodes", nodes);
        body.put("document_refs", documentRefs);
        body.put("risk_summary", summary == null ? null : summary.get("risk_summary"));
        body.put("performance_summary", summary);
        return body;
    }

    ResponseEntity<Map<String, Object>> createPerformanceNode(String contractId, Map<String, Object> request) {
        requireContract(contractId);
        PerformanceRecordState record = performanceRecordForContract(contractId);
        if (record == null) {
            ResponseEntity<Map<String, Object>> created = createPerformanceRecord(contractId, request);
            if (!created.getStatusCode().is2xxSuccessful()) {
                return created;
            }
            record = performanceRecordForContract(contractId);
        }
        String requestedRecordId = text(request, "performance_record_id", record.performanceRecordId());
        if (!record.performanceRecordId().equals(requestedRecordId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PERFORMANCE_RECORD_CONTRACT_MISMATCH", "履约记录未绑定当前合同"));
        }

        String nodeId = "perf-node-" + UUID.randomUUID();
        Map<String, Object> documentRef = performanceDocumentRef(contractId, nodeId, request);
        PerformanceNodeState node = new PerformanceNodeState(nodeId, record.performanceRecordId(), contractId,
                text(request, "node_type", "GENERAL"), text(request, "node_name", "履约节点"), text(request, "milestone_code", null),
                text(request, "planned_at", null), text(request, "due_at", null), null, "PENDING", intValue(request, "progress_percent", 0),
                text(request, "risk_level", "LOW"), intValue(request, "issue_count", 0), false, text(request, "result_summary", null), null,
                text(request, "owner_user_id", record.ownerUserId()), text(request, "owner_org_unit_id", record.ownerOrgUnitId()), documentRef);
        performanceNodes.put(nodeId, node);
        Map<String, Object> summary = refreshPerformanceSummary(contractId, node.milestoneCode());
        lifecycleSummaries.put(contractId, lifecycleSummary(contractId, summary));
        appendContractEvent(contractId, "PERFORMANCE_NODE_CREATED", nodeId, text(request, "trace_id", null), null);
        Map<String, Object> body = performanceNodeBody(node);
        body.put("performance_summary", summary);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    ResponseEntity<Map<String, Object>> updatePerformanceNode(String contractId, String nodeId, Map<String, Object> request) {
        PerformanceNodeState node = requirePerformanceNode(nodeId);
        if (!contractId.equals(node.contractId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PERFORMANCE_NODE_CONTRACT_MISMATCH", "履约节点未绑定当前合同"));
        }
        String previousRisk = node.riskLevel();
        String nodeStatus = text(request, "node_status", node.nodeStatus());
        String riskLevel = text(request, "risk_level", node.riskLevel());
        boolean overdue = bool(request, "is_overdue", "OVERDUE".equals(nodeStatus));
        PerformanceNodeState updated = new PerformanceNodeState(node.performanceNodeId(), node.performanceRecordId(), node.contractId(),
                node.nodeType(), node.nodeName(), node.milestoneCode(), node.plannedAt(), node.dueAt(), text(request, "actual_at", node.actualAt()),
                nodeStatus, intValue(request, "progress_percent", node.progressPercent()), riskLevel, intValue(request, "issue_count", node.issueCount()),
                overdue, text(request, "result_summary", node.resultSummary()), Instant.now().toString(), node.ownerUserId(), node.ownerOrgUnitId(), node.documentRef());
        performanceNodes.put(nodeId, updated);

        String milestoneCode = "COMPLETED".equals(nodeStatus) ? "PERFORMANCE_COMPLETED" : updated.milestoneCode();
        Map<String, Object> summary = refreshPerformanceSummary(contractId, milestoneCode);
        lifecycleSummaries.put(contractId, lifecycleSummary(contractId, summary));
        if (!previousRisk.equals(riskLevel)) {
            appendContractEvent(contractId, "PERFORMANCE_RISK_CHANGED", nodeId, text(request, "trace_id", null), null);
        }
        if (overdue || "OVERDUE".equals(nodeStatus)) {
            appendContractEvent(contractId, "PERFORMANCE_NODE_OVERDUE", nodeId, text(request, "trace_id", null), null);
        }
        if ("COMPLETED".equals(summary.get("performance_status"))) {
            appendContractEvent(contractId, "PERFORMANCE_COMPLETED", nodeId, text(request, "trace_id", null), "PERFORMED");
        } else {
            appendContractEvent(contractId, "PERFORMANCE_PROGRESS_UPDATED", nodeId, text(request, "trace_id", null), null);
        }

        Map<String, Object> body = performanceNodeBody(updated);
        body.put("performance_summary", summary);
        return ResponseEntity.ok(body);
    }

    Map<String, Object> batch3SharedContract() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status_fields", List.of("signature_status", "encryption_status", "performance_status", "change_status", "termination_status", "archive_status"));

        Map<String, Object> statusMapping = new LinkedHashMap<>();
        statusMapping.put("signature_status", Map.of(
                "READY", Map.of("allowed_contract_status", List.of("APPROVED"), "writeback_contract_status", "UNCHANGED"),
                "SIGNED", Map.of("allowed_contract_status", List.of("APPROVED"), "writeback_contract_status", "SIGNED")));
        statusMapping.put("encryption_status", Map.of(
                "PENDING", Map.of("allowed_contract_status", List.of("DRAFT", "UNDER_APPROVAL", "APPROVED", "SIGNED"), "writeback_contract_status", "UNCHANGED"),
                "ENCRYPTED", Map.of("allowed_contract_status", List.of("DRAFT", "UNDER_APPROVAL", "APPROVED", "SIGNED"), "writeback_contract_status", "UNCHANGED")));
        statusMapping.put("performance_status", Map.of(
                "IN_PROGRESS", Map.of("allowed_contract_status", List.of("SIGNED"), "writeback_contract_status", "UNCHANGED"),
                "COMPLETED", Map.of("allowed_contract_status", List.of("SIGNED"), "writeback_contract_status", "PERFORMED")));
        statusMapping.put("change_status", Map.of(
                "APPROVING", Map.of("allowed_contract_status", List.of("SIGNED", "PERFORMED"), "writeback_contract_status", "CHANGE_UNDER_APPROVAL"),
                "APPROVED", Map.of("allowed_contract_status", List.of("SIGNED", "PERFORMED"), "writeback_contract_status", "CHANGED")));
        statusMapping.put("termination_status", Map.of(
                "APPROVING", Map.of("allowed_contract_status", List.of("SIGNED", "PERFORMED", "CHANGED"), "writeback_contract_status", "TERMINATION_UNDER_APPROVAL"),
                "TERMINATED", Map.of("allowed_contract_status", List.of("SIGNED", "PERFORMED", "CHANGED"), "writeback_contract_status", "TERMINATED")));
        statusMapping.put("archive_status", Map.of(
                "READY", Map.of("allowed_contract_status", List.of("SIGNED", "PERFORMED", "CHANGED", "TERMINATED"), "writeback_contract_status", "UNCHANGED"),
                "ARCHIVED", Map.of("allowed_contract_status", List.of("SIGNED", "PERFORMED", "CHANGED", "TERMINATED"), "writeback_contract_status", "ARCHIVED")));
        body.put("status_contract_mapping", statusMapping);

        Map<String, Object> writeback = new LinkedHashMap<>();
        writeback.put("signature_status", "e-signature -> contract.signature_summary");
        writeback.put("encryption_status", "encrypted-document -> document-center.encryption_summary -> contract.document_summary");
        writeback.put("performance_status", "contract-lifecycle -> contract.performance_summary");
        writeback.put("change_status", "contract-lifecycle -> contract.change_summary");
        writeback.put("termination_status", "contract-lifecycle -> contract.termination_summary");
        writeback.put("archive_status", "contract-lifecycle -> contract.archive_summary");
        body.put("summary_writeback_direction", writeback);

        body.put("truth_ownership", Map.of(
                "contract_master", "reference contract_id only",
                "document_version", "reference document_version_id only",
                "approval", "reference approval_summary only"));
        return body;
    }

    Map<String, Object> batch3MountPoints(String contractId, String signatureStatus) {
        ContractState contract = requireContract(contractId);
        DocumentRef document = contract.currentDocument();
        boolean approved = "APPROVED".equals(contract.contractStatus())
                && contract.approvalSummary() != null
                && "APPROVED".equals(contract.approvalSummary().get("final_result"));
        String normalizedSignatureStatus = signatureStatus == null || signatureStatus.isBlank() ? "NOT_STARTED" : signatureStatus;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contract.contractId());
        body.put("contract_status", contract.contractStatus());
        body.put("approval_summary", contract.approvalSummary());
        body.put("signature_input", batch3SignatureInput(contract, document, approved));
        body.put("encryption_input", batch3DocumentInput(document));
        body.put("archive_input", batch3DocumentInput(document));
        body.put("performance_input", batch3PerformanceInput(contract, normalizedSignatureStatus));
        body.put("main_truth_reuse", Map.of(
                "contract_master_owner", "contract-core",
                "document_version_owner", "document-center",
                "approval_summary_owner", "workflow-engine"));
        return body;
    }

    private Map<String, Object> batch3SignatureInput(ContractState contract, DocumentRef document, boolean approved) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("accepted", approved && document != null);
        input.put("contract_id", contract.contractId());
        input.put("document_version_ref", document == null ? null : batch3DocumentVersionRef(document));
        input.put("approval_summary", contract.approvalSummary());
        return input;
    }

    private ResponseEntity<Map<String, Object>> signatureAdmissionRejection(ContractState contract, DocumentRef currentDocument,
                                                                            String documentAssetId, String documentVersionId) {
        String finalResult = contract.approvalSummary() == null ? null : String.valueOf(contract.approvalSummary().get("final_result"));
        if ("TERMINATED".equals(finalResult)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("APPROVAL_WITHDRAWN", "审批已撤回或终止，不能发起签章"));
        }
        if (!"APPROVED".equals(finalResult)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("APPROVAL_NOT_PASSED", "审批未通过，不能发起签章"));
        }
        if (!"APPROVED".equals(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONTRACT_STATUS_CONFLICT", "合同状态不允许发起签章"));
        }
        if (currentDocument == null
                || !currentDocument.documentAssetId().equals(documentAssetId)
                || !currentDocument.documentVersionId().equals(documentVersionId)) {
            Map<String, Object> body = error("FILE_VALIDATION_FAILED", "签章输入稿不是合同当前有效正式版本");
            body.put("current_document_version_id", currentDocument == null ? null : currentDocument.documentVersionId());
            body.put("requested_document_version_id", documentVersionId);
            return ResponseEntity.unprocessableEntity().body(body);
        }
        String approvedDocumentVersionId = approvedDocumentVersionId(contract);
        if (!documentVersionId.equals(approvedDocumentVersionId)) {
            Map<String, Object> body = error("FILE_VALIDATION_FAILED", "签章输入稿不是审批通过版本");
            body.put("approved_document_version_id", approvedDocumentVersionId);
            body.put("requested_document_version_id", documentVersionId);
            return ResponseEntity.unprocessableEntity().body(body);
        }
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        DocumentVersionState version = requireDocumentVersion(documentVersionId);
        if (!"CONTRACT".equals(asset.ownerType()) || !contract.contractId().equals(asset.ownerId())
                || !asset.documentAssetId().equals(version.documentAssetId())) {
            return ResponseEntity.unprocessableEntity().body(error("FILE_VALIDATION_FAILED", "签章输入稿未绑定当前合同主链"));
        }
        return null;
    }

    private String approvedDocumentVersionId(ContractState contract) {
        ProcessState process = contract.processId() == null ? null : processes.get(contract.processId());
        return process == null ? null : process.documentVersionId();
    }

    private Map<String, Object> signatureApplicationSnapshot(ContractState contract) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contract_id", contract.contractId());
        snapshot.put("contract_status", contract.contractStatus());
        snapshot.put("approval_summary", contract.approvalSummary() == null ? null : new LinkedHashMap<>(contract.approvalSummary()));
        snapshot.put("contract_snapshot_version", contract.processId());
        return snapshot;
    }

    private Map<String, Object> signatureInputBinding(String contractId, String signatureRequestId, String documentAssetId, String documentVersionId) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("binding_id", "sig-bind-" + UUID.randomUUID());
        binding.put("contract_id", contractId);
        binding.put("signature_request_id", signatureRequestId);
        binding.put("binding_role", "SOURCE_MAIN");
        binding.put("binding_status", "BOUND");
        binding.put("document_asset_id", documentAssetId);
        binding.put("document_version_id", documentVersionId);
        binding.put("bound_at", Instant.now().toString());
        return binding;
    }

    private Map<String, Object> signatureAudit(String actionType, String contractId, String signatureRequestId, String traceId) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("signature_audit_event_id", "sig-audit-" + UUID.randomUUID());
        audit.put("audit_action_type", actionType);
        audit.put("audit_level", "HIGH");
        audit.put("contract_id", contractId);
        audit.put("signature_request_id", signatureRequestId);
        audit.put("audit_result", "SUCCESS");
        audit.put("trace_id", traceId);
        audit.put("occurred_at", Instant.now().toString());
        return audit;
    }

    private Map<String, Object> signatureRequestBody(SignatureRequestState state, boolean idempotencyReplayed, boolean includeAdmissionDetails) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signature_request_id", state.signatureRequestId());
        body.put("contract_id", state.contractId());
        body.put("request_status", state.requestStatus());
        body.put("signature_status", state.signatureStatus());
        body.put("main_document_asset_id", state.mainDocumentAssetId());
        body.put("main_document_version_id", state.mainDocumentVersionId());
        body.put("signature_mode", state.signatureMode());
        body.put("seal_scheme_id", state.sealSchemeId());
        body.put("sign_order_mode", state.signOrderMode());
        body.put("request_fingerprint", state.requestFingerprint());
        body.put("created_at", state.createdAt());
        body.put("created_by", state.createdBy());
        if (state.currentSessionId() != null) {
            body.put("current_session_id", state.currentSessionId());
        }
        if (state.latestResultId() != null) {
            body.put("latest_result_id", state.latestResultId());
        }
        if (idempotencyReplayed) {
            body.put("idempotency_replayed", true);
        }
        if (includeAdmissionDetails) {
            body.put("application_snapshot", state.applicationSnapshot());
            body.put("input_document_binding", state.inputDocumentBinding());
            body.put("audit_record", state.auditRecords());
        }
        return body;
    }

    private List<Map<String, Object>> buildSignerAssignments(String sessionId, SignatureRequestState signatureRequest, Map<String, Object> request) {
        List<Map<String, Object>> signerList = listMaps(request.get("signer_list"));
        List<Map<String, Object>> assignments = new ArrayList<>();
        for (int index = 0; index < signerList.size(); index++) {
            Map<String, Object> signer = signerList.get(index);
            int sequence = index + 1;
            Map<String, Object> assignment = new LinkedHashMap<>();
            assignment.put("signer_assignment_id", "sig-assign-" + UUID.randomUUID());
            assignment.put("signature_request_id", signatureRequest.signatureRequestId());
            assignment.put("signature_session_id", sessionId);
            assignment.put("contract_id", signatureRequest.contractId());
            assignment.put("signer_type", text(signer, "signer_type", "USER"));
            assignment.put("signer_id", text(signer, "signer_id", null));
            assignment.put("assignment_role", text(signer, "assignment_role", "PRIMARY_SIGNER"));
            assignment.put("sign_sequence_no", sequence);
            assignment.put("assignment_status", sequence == 1 ? "READY" : "WAITING");
            assignment.put("assignment_source", "SESSION_REQUEST");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("signer_id", text(signer, "signer_id", null));
            snapshot.put("signer_name", text(signer, "signer_name", text(signer, "signer_id", null)));
            snapshot.put("signer_org", text(signer, "signer_org", null));
            assignment.put("signer_snapshot", snapshot);
            Map<String, Object> taskCenterRef = new LinkedHashMap<>();
            taskCenterRef.put("task_center_task_id", "task-center-" + assignment.get("signer_assignment_id"));
            taskCenterRef.put("task_center_status", sequence == 1 ? "PUBLISHED" : "WAITING_PREVIOUS_SIGNER");
            assignment.put("task_center_ref", taskCenterRef);
            assignments.add(assignment);
        }
        return assignments;
    }

    private Map<String, Object> signatureSessionBody(SignatureSessionState session, String manualReasonCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signature_session_id", session.signatureSessionId());
        body.put("signature_request_id", session.signatureRequestId());
        body.put("contract_id", session.contractId());
        body.put("session_status", session.sessionStatus());
        body.put("sign_order_mode", session.signOrderMode());
        body.put("current_sign_step", session.currentSignStep());
        body.put("pending_signer_count", session.pendingSignerCount());
        body.put("completed_signer_count", session.completedSignerCount());
        body.put("expires_at", session.expiresAt());
        body.put("assignment_list", session.assignments());
        body.put("task_center_items", session.assignments().stream().map(item -> item.get("task_center_ref")).toList());
        body.put("notification_items", session.assignments().stream()
                .filter(item -> "READY".equals(text(item, "assignment_status", null)))
                .map(item -> Map.of("signer_id", item.get("signer_id"), "notification_status", "SENT"))
                .toList());
        if (manualReasonCode != null) {
            body.put("manual_intervention", Map.of("required", true, "reason_code", manualReasonCode));
        }
        return body;
    }

    private boolean isSignatureSessionAdvanceable(SignatureSessionState session) {
        return "OPEN".equals(session.sessionStatus()) || "PARTIALLY_SIGNED".equals(session.sessionStatus());
    }

    private SignatureSessionState sessionWithManualIntervention(SignatureSessionState session, String reasonCode, String externalEventId) {
        List<String> callbacks = copyCallbackEventIds(session.callbackEventIds());
        if (externalEventId != null && !callbacks.contains(externalEventId)) {
            callbacks.add(externalEventId);
        }
        return new SignatureSessionState(session.signatureSessionId(), session.signatureRequestId(), session.contractId(), "FAILED",
                session.sessionRound(), session.signOrderMode(), session.currentSignStep(), session.pendingSignerCount(), session.completedSignerCount(),
                session.startedAt(), session.expiresAt(), null, reasonCode, externalEventId, callbacks,
                copyAssignmentList(session.assignments()));
    }

    private Map<String, Object> assignmentBySigner(SignatureSessionState session, String signerId) {
        return session.assignments().stream()
                .filter(item -> signerId.equals(text(item, "signer_id", null)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("signer_id 不存在: " + signerId));
    }

    private Map<String, Object> createSignatureDocumentBinding(String contractId, String signatureRequestId, String signatureResultId,
                                                              String bindingRole, String fileToken, String traceId) {
        String title = "SIGNED_MAIN".equals(bindingRole) ? "签章结果稿.pdf" : "签章验签产物.json";
        Map<String, Object> document = createDocumentAsset(Map.of(
                "owner_type", "CONTRACT",
                "owner_id", contractId,
                "document_role", bindingRole,
                "document_title", title,
                "source_channel", "E_SIGNATURE_WRITEBACK",
                "file_upload_token", fileToken == null ? "generated-" + UUID.randomUUID() : fileToken,
                "trace_id", traceId == null ? "trace-es-writeback" : traceId));
        return signatureDocumentBinding(contractId, signatureRequestId, signatureResultId, null,
                text(document, "document_asset_id", null), text(document, "document_version_id", null), bindingRole);
    }

    private Map<String, Object> signatureDocumentBinding(String contractId, String signatureRequestId, String signatureResultId, String paperRecordId,
                                                         String documentAssetId, String documentVersionId, String bindingRole) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("binding_id", "sig-bind-" + UUID.randomUUID());
        binding.put("contract_id", contractId);
        binding.put("signature_request_id", signatureRequestId);
        binding.put("signature_result_id", signatureResultId);
        binding.put("paper_record_id", paperRecordId);
        binding.put("binding_role", bindingRole);
        binding.put("binding_status", "BOUND");
        binding.put("document_asset_id", documentAssetId);
        binding.put("document_version_id", documentVersionId);
        binding.put("bound_at", Instant.now().toString());
        return binding;
    }

    private Map<String, Object> signatureResultBody(SignatureResultState result, Map<String, Object> compensationTask) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signature_result_id", result.signatureResultId());
        body.put("signature_request_id", result.signatureRequestId());
        body.put("signature_session_id", result.signatureSessionId());
        body.put("contract_id", result.contractId());
        body.put("result_status", result.resultStatus());
        body.put("verification_status", result.verificationStatus());
        body.put("document_writeback_status", result.documentWritebackStatus());
        body.put("contract_writeback_status", result.contractWritebackStatus());
        body.put("latest_signed_document_asset_id", result.signedDocumentAssetId());
        body.put("latest_signed_document_version_id", result.signedDocumentVersionId());
        body.put("document_binding_order", result.documentBindings());
        body.put("contract_signature_summary", result.contractSignatureSummary());
        if (compensationTask != null) {
            body.put("compensation_task", compensationTask);
        }
        return body;
    }

    private Map<String, Object> signatureSummary(String contractId, String signatureStatus, String signatureRequestId, String signatureResultId,
                                                 String signedAssetId, String signedVersionId, String signatureMode, String verificationStatus,
                                                 String rebuildSource) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contract_id", contractId);
        summary.put("signature_status", signatureStatus);
        summary.put("latest_signature_request_id", signatureRequestId);
        summary.put("latest_signature_result_id", signatureResultId);
        summary.put("latest_signed_document_asset_id", signedAssetId);
        summary.put("latest_signed_document_version_id", signedVersionId);
        summary.put("signature_mode", signatureMode);
        summary.put("signed_at", Instant.now().toString());
        summary.put("verification_status", verificationStatus);
        summary.put("display_text", "SIGNED".equals(signatureStatus) ? "电子签章已完成" : "纸质签署已备案");
        summary.put("rebuild_source", rebuildSource);
        return summary;
    }

    private void writeBackContractSignatureSummary(String contractId, Map<String, Object> summary, String resultId, String traceId) {
        ContractState contract = requireContract(contractId);
        signatureSummaries.put(contractId, summary);
        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), "SIGNED",
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                copyDocuments(contract.attachments()), contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event("SIGNATURE_RESULT_WRITEBACK_COMPLETED", resultId, traceId));
        contracts.put(contractId, updated);
    }

    private Map<String, Object> compensationTask(String taskType, String contractId, String processId, String traceId) {
        String taskId = "cmp-task-" + UUID.randomUUID();
        CompensationTaskState task = new CompensationTaskState(taskId, taskType, contractId, processId, "PENDING_RETRY", traceId, Instant.now().toString());
        compensationTasks.put(taskId, task);
        return compensationTaskBody(task);
    }

    private Map<String, Object> paperRecordBody(PaperRecordState record, boolean coexistenceAllowed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paper_record_id", record.paperRecordId());
        body.put("contract_id", record.contractId());
        body.put("signature_request_id", record.signatureRequestId());
        body.put("record_status", record.recordStatus());
        body.put("recorded_sign_date", record.recordedSignDate());
        body.put("paper_document_asset_id", record.paperDocumentAssetId());
        body.put("paper_document_version_id", record.paperDocumentVersionId());
        body.put("confirmed_by", record.confirmedBy());
        body.put("confirmed_at", record.confirmedAt());
        body.put("paper_scan_binding", record.paperScanBinding());
        body.put("signature_summary", record.signatureSummary());
        body.put("coexistence", Map.of("allowed", coexistenceAllowed));
        return body;
    }

    private Map<String, Object> batch3DocumentInput(DocumentRef document) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("document_version_ref", document == null ? null : batch3DocumentVersionRef(document));
        return input;
    }

    private Map<String, Object> batch3PerformanceInput(ContractState contract, String signatureStatus) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("accepted", "SIGNED".equals(contract.contractStatus()));
        input.put("contract_id", contract.contractId());
        input.put("source_contract_status", contract.contractStatus());
        input.put("signature_status", signatureStatus);
        return input;
    }

    private Map<String, Object> batch3DocumentVersionRef(DocumentRef document) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("document_asset_id", document.documentAssetId());
        ref.put("document_version_id", document.documentVersionId());
        ref.put("effective_document_version_id", document.effectiveDocumentVersionId());
        return ref;
    }

    private Map<String, Object> performanceRecordBody(PerformanceRecordState record, String sourceContractStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("performance_record_id", record.performanceRecordId());
        body.put("contract_id", record.contractId());
        body.put("performance_status", record.performanceStatus());
        body.put("progress_percent", record.progressPercent());
        body.put("risk_level", record.riskLevel());
        body.put("owner_user_id", record.ownerUserId());
        body.put("owner_org_unit_id", record.ownerOrgUnitId());
        body.put("open_node_count", record.openNodeCount());
        body.put("overdue_node_count", record.overdueNodeCount());
        body.put("latest_due_at", record.latestDueAt());
        body.put("summary_text", record.summaryText());
        body.put("latest_milestone_code", record.latestMilestoneCode());
        body.put("source_contract_status", sourceContractStatus);
        return body;
    }

    private Map<String, Object> performanceNodeBody(PerformanceNodeState node) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("performance_node_id", node.performanceNodeId());
        body.put("performance_record_id", node.performanceRecordId());
        body.put("contract_id", node.contractId());
        body.put("node_type", node.nodeType());
        body.put("node_name", node.nodeName());
        body.put("milestone_code", node.milestoneCode());
        body.put("planned_at", node.plannedAt());
        body.put("due_at", node.dueAt());
        body.put("actual_at", node.actualAt());
        body.put("node_status", node.nodeStatus());
        body.put("progress_percent", node.progressPercent());
        body.put("risk_level", node.riskLevel());
        body.put("issue_count", node.issueCount());
        body.put("is_overdue", node.overdue());
        body.put("result_summary", node.resultSummary());
        body.put("last_result_at", node.lastResultAt());
        body.put("owner_user_id", node.ownerUserId());
        body.put("owner_org_unit_id", node.ownerOrgUnitId());
        body.put("document_ref", node.documentRef());
        return body;
    }

    private Map<String, Object> performanceDocumentRef(String contractId, String nodeId, Map<String, Object> request) {
        String documentAssetId = text(request, "evidence_document_asset_id", null);
        String documentVersionId = text(request, "evidence_document_version_id", null);
        if (documentAssetId == null && documentVersionId == null) {
            return null;
        }
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        DocumentVersionState version = requireDocumentVersion(documentVersionId);
        if (!contractId.equals(asset.ownerId()) || !asset.documentAssetId().equals(version.documentAssetId())) {
            throw new IllegalArgumentException("履约凭证必须引用当前合同的文档中心版本");
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("lifecycle_document_ref_id", "cl-doc-ref-" + UUID.randomUUID());
        ref.put("contract_id", contractId);
        ref.put("source_resource_type", "PERFORMANCE_NODE");
        ref.put("source_resource_id", nodeId);
        ref.put("document_role", "PERFORMANCE_EVIDENCE");
        ref.put("document_asset_id", documentAssetId);
        ref.put("document_version_id", documentVersionId);
        ref.put("is_primary", true);
        return ref;
    }

    private Map<String, Object> refreshPerformanceSummary(String contractId, String milestoneCode) {
        PerformanceRecordState record = performanceRecordForContract(contractId);
        List<PerformanceNodeState> nodes = performanceNodes.values().stream()
                .filter(node -> contractId.equals(node.contractId()))
                .toList();
        int nodeCount = nodes.size();
        int completedCount = (int) nodes.stream().filter(node -> "COMPLETED".equals(node.nodeStatus())).count();
        int overdueCount = (int) nodes.stream().filter(node -> node.overdue() || "OVERDUE".equals(node.nodeStatus())).count();
        int openCount = nodeCount - completedCount;
        int progress = nodeCount == 0 ? 0 : nodes.stream().mapToInt(PerformanceNodeState::progressPercent).sum() / nodeCount;
        String riskLevel = nodes.stream().map(PerformanceNodeState::riskLevel).reduce("LOW", this::higherRisk);
        String status = nodeCount > 0 && completedCount == nodeCount ? "COMPLETED" : (overdueCount > 0 || "HIGH".equals(riskLevel) ? "AT_RISK" : "IN_PROGRESS");
        String latestDueAt = nodes.stream().map(PerformanceNodeState::dueAt).filter(value -> value != null && !value.isBlank()).min(String::compareTo).orElse(null);
        String latestMilestone = "COMPLETED".equals(status) ? "PERFORMANCE_COMPLETED" : (milestoneCode == null ? "PERFORMANCE_STARTED" : milestoneCode);
        String summaryText = switch (status) {
            case "COMPLETED" -> "履约已完成";
            case "AT_RISK" -> "履约存在风险";
            default -> "履约进行中";
        };

        if (record != null) {
            PerformanceRecordState updated = new PerformanceRecordState(record.performanceRecordId(), record.contractId(), status, progress, riskLevel,
                    record.ownerUserId(), record.ownerOrgUnitId(), openCount, overdueCount, latestDueAt, summaryText, latestMilestone,
                    Instant.now().toString(), Instant.now().toString());
            performanceRecords.put(record.performanceRecordId(), updated);
        }

        Map<String, Object> riskSummary = new LinkedHashMap<>();
        riskSummary.put("risk_level", riskLevel);
        riskSummary.put("overdue_node_count", overdueCount);
        riskSummary.put("issue_count", nodes.stream().mapToInt(PerformanceNodeState::issueCount).sum());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contract_id", contractId);
        summary.put("performance_record_id", record == null ? null : record.performanceRecordId());
        summary.put("performance_status", status);
        summary.put("progress_percent", progress);
        summary.put("open_node_count", openCount);
        summary.put("overdue_node_count", overdueCount);
        summary.put("risk_summary", riskSummary);
        summary.put("latest_milestone_code", latestMilestone);
        summary.put("summary_text", summaryText);
        summary.put("last_contract_writeback_at", Instant.now().toString());
        performanceSummaries.put(contractId, summary);
        return summary;
    }

    private Map<String, Object> lifecycleSummary(String contractId, Map<String, Object> performanceSummary) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contract_id", contractId);
        summary.put("current_stage", "PERFORMANCE");
        summary.put("stage_status", performanceSummary.get("performance_status"));
        summary.put("performance_summary", performanceSummary);
        summary.put("risk_summary", performanceSummary.get("risk_summary"));
        summary.put("latest_milestone_code", performanceSummary.get("latest_milestone_code"));
        summary.put("latest_milestone_at", Instant.now().toString());
        summary.put("summary_version", "cl-summary-v1");
        return summary;
    }

    private String higherRisk(String left, String right) {
        return riskRank(right) > riskRank(left) ? right : left;
    }

    private int riskRank(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private void appendContractEvent(String contractId, String eventType, String objectId, String traceId, String nextContractStatus) {
        ContractState contract = requireContract(contractId);
        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(),
                nextContractStatus == null ? contract.contractStatus() : nextContractStatus,
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                copyDocuments(contract.attachments()), contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event(eventType, objectId, traceId));
        contracts.put(contractId, updated);
    }

    private PerformanceRecordState performanceRecordForContract(String contractId) {
        String recordId = performanceRecordByContract.get(contractId);
        return recordId == null ? null : performanceRecords.get(recordId);
    }

    private Map<String, Object> acceptEncryptionCheckIn(String documentAssetId, String documentVersionId, String triggerType,
                                                        Map<String, Object> request, boolean replayAsIdempotent) {
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        DocumentVersionState version = requireDocumentVersion(documentVersionId);
        if (!documentAssetId.equals(version.documentAssetId())) {
            throw new IllegalArgumentException("document_version_id 未绑定目标文档资产");
        }
        String indexKey = documentVersionId + ":" + triggerType;
        String existingCheckInId = encryptionCheckInByVersionTrigger.get(indexKey);
        if (existingCheckInId != null) {
            return encryptionCheckInBody(requireEncryptionCheckIn(existingCheckInId), true);
        }

        EncryptionSecurityBindingState binding = findOrCreateEncryptionBinding(asset, documentVersionId);
        String checkInId = "ed-check-in-" + UUID.randomUUID();
        String jobId = "cmp-task-" + UUID.randomUUID();
        boolean failed = bool(request, "simulate_encryption_failure", false);
        String status = failed ? "FAILED_RETRYABLE" : "SUCCEEDED";
        String resultStatus = failed ? "REJECTED" : "ENCRYPTED";
        String resultCode = failed ? "ENCRYPTION_TASK_FAILED" : "ENCRYPTION_COMPLETED";
        EncryptionCheckInState checkIn = new EncryptionCheckInState(checkInId, binding.securityBindingId(), documentAssetId, documentVersionId,
                asset.ownerId(), triggerType, status, resultStatus, indexKey, resultCode, failed ? "自动加密任务失败，文档真相保持不变" : "自动加密完成",
                jobId, Instant.now().toString(), Instant.now().toString());
        encryptionCheckIns.put(checkInId, checkIn);
        encryptionCheckInByVersionTrigger.put(indexKey, checkInId);

        EncryptionSecurityBindingState updatedBinding = new EncryptionSecurityBindingState(binding.securityBindingId(), documentAssetId, documentVersionId,
                asset.ownerId(), failed ? "FAILED" : "ENCRYPTED", "PLATFORM_CONTROLLED", "AUTHORIZED_ONLY", checkInId,
                failed ? binding.lastSuccessfulEncryptedVersionId() : documentVersionId, Instant.now().toString(), binding.securityVersionNo() + 1);
        encryptionSecurityBindings.put(updatedBinding.securityBindingId(), updatedBinding);

        List<Map<String, Object>> audits = copyEvents(asset.auditRecords());
        audits.add(encryptionAudit("CHECK_IN_ACCEPTED", "SUCCESS", updatedBinding.securityBindingId(), documentAssetId, documentVersionId,
                asset.ownerId(), "SYSTEM", "encrypted-document", null, checkInId, text(request, "trace_id", null)));
        audits.add(encryptionAudit(failed ? "ENCRYPT_FAILED" : "ENCRYPT_SUCCEEDED", failed ? "FAILED" : "SUCCESS", updatedBinding.securityBindingId(), documentAssetId,
                documentVersionId, asset.ownerId(), "SYSTEM", "encrypted-document", null, checkInId, text(request, "trace_id", null)));
        documentAssets.put(documentAssetId, new DocumentAssetState(asset.documentAssetId(), asset.currentVersionId(), asset.ownerType(), asset.ownerId(),
                asset.documentRole(), asset.documentTitle(), asset.documentStatus(), failed ? "FAILED" : "ENCRYPTED", asset.previewStatus(),
                asset.latestVersionNo(), audits));

        Map<String, Object> body = encryptionCheckInBody(checkIn, replayAsIdempotent && existingCheckInId != null);
        body.put("security_binding", securityBindingBody(updatedBinding));
        return body;
    }

    private EncryptionSecurityBindingState findOrCreateEncryptionBinding(DocumentAssetState asset, String documentVersionId) {
        String existingBindingId = encryptionBindingByDocumentAsset.get(asset.documentAssetId());
        if (existingBindingId != null) {
            EncryptionSecurityBindingState existing = requireEncryptionBinding(existingBindingId);
            EncryptionSecurityBindingState updated = new EncryptionSecurityBindingState(existing.securityBindingId(), existing.documentAssetId(), documentVersionId,
                    existing.contractId(), "PENDING", existing.internalAccessMode(), existing.downloadControlMode(), existing.latestCheckInId(),
                    existing.lastSuccessfulEncryptedVersionId(), existing.lastSecurityEventAt(), existing.securityVersionNo() + 1);
            encryptionSecurityBindings.put(updated.securityBindingId(), updated);
            return updated;
        }
        String bindingId = "ed-bind-" + UUID.randomUUID();
        EncryptionSecurityBindingState binding = new EncryptionSecurityBindingState(bindingId, asset.documentAssetId(), documentVersionId,
                asset.ownerId(), "PENDING", "PLATFORM_CONTROLLED", "AUTHORIZED_ONLY", null, null, Instant.now().toString(), 1);
        encryptionSecurityBindings.put(bindingId, binding);
        encryptionBindingByDocumentAsset.put(asset.documentAssetId(), bindingId);
        return binding;
    }

    private Map<String, Object> securityBindingBody(EncryptionSecurityBindingState binding) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("security_binding_id", binding.securityBindingId());
        body.put("document_asset_id", binding.documentAssetId());
        body.put("current_version_id", binding.currentVersionId());
        body.put("contract_id", binding.contractId());
        body.put("encryption_status", binding.encryptionStatus());
        body.put("internal_access_mode", binding.internalAccessMode());
        body.put("download_control_mode", binding.downloadControlMode());
        body.put("last_successful_encrypted_version_id", binding.lastSuccessfulEncryptedVersionId());
        body.put("security_version_no", binding.securityVersionNo());
        if (binding.latestCheckInId() != null) {
            body.put("latest_check_in", encryptionCheckInBody(requireEncryptionCheckIn(binding.latestCheckInId()), false));
        }
        return body;
    }

    private Map<String, Object> encryptionCheckInBody(EncryptionCheckInState checkIn, boolean idempotencyReplayed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("check_in_id", checkIn.checkInId());
        body.put("security_binding_id", checkIn.securityBindingId());
        body.put("document_asset_id", checkIn.documentAssetId());
        body.put("document_version_id", checkIn.documentVersionId());
        body.put("contract_id", checkIn.contractId());
        body.put("trigger_type", checkIn.triggerType());
        body.put("check_in_status", checkIn.checkInStatus());
        body.put("encryption_result_status", checkIn.encryptionResultStatus());
        body.put("idempotency_key", checkIn.idempotencyKey());
        body.put("result_code", checkIn.resultCode());
        body.put("result_message", checkIn.resultMessage());
        body.put("platform_job_ref", Map.of("platform_job_id", checkIn.platformJobId(), "job_type", "ED_ENCRYPTION_CHECK_IN", "job_status", checkIn.checkInStatus()));
        body.put("accepted_at", checkIn.acceptedAt());
        body.put("completed_at", checkIn.completedAt());
        if (idempotencyReplayed) {
            body.put("idempotency_replayed", true);
        }
        return body;
    }

    private String consumptionMode(String scene) {
        return switch (scene) {
            case "SIGNATURE", "ARCHIVE" -> "INTERNAL_HANDLE";
            case "SEARCH", "AI" -> "TEMP_TEXT";
            default -> "STREAM";
        };
    }

    private boolean allowedAccessScene(String scene) {
        return List.of("PREVIEW", "SIGNATURE", "ARCHIVE", "SEARCH", "AI").contains(scene);
    }

    private Map<String, Object> decryptAccessBody(DecryptAccessState access, Map<String, Object> audit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decrypt_access_id", access.decryptAccessId());
        body.put("security_binding_id", access.securityBindingId());
        body.put("document_asset_id", access.documentAssetId());
        body.put("document_version_id", access.documentVersionId());
        body.put("contract_id", access.contractId());
        body.put("access_scene", access.accessScene());
        body.put("access_subject_type", access.accessSubjectType());
        body.put("access_subject_id", access.accessSubjectId());
        body.put("access_result", access.accessResult());
        body.put("decision_reason_code", access.decisionReasonCode());
        body.put("access_ticket", access.accessTicket());
        body.put("ticket_expires_at", access.ticketExpiresAt());
        body.put("consumption_mode", access.consumptionMode());
        body.put("controlled_read_handle", Map.of(
                "handle_id", "ed-handle-" + access.decryptAccessId(),
                "handle_status", access.accessResult(),
                "plaintext_export_allowed", false,
                "cache_policy", "NO_PERSISTENT_PLAINTEXT"));
        body.put("audit_event", audit);
        return body;
    }

    private DecryptAccessState updateDecryptAccessResult(DecryptAccessState access, String accessResult, String reasonCode) {
        DecryptAccessState updated = new DecryptAccessState(access.decryptAccessId(), access.securityBindingId(), access.documentAssetId(),
                access.documentVersionId(), access.contractId(), access.accessScene(), access.accessSubjectType(), access.accessSubjectId(),
                access.actorDepartmentId(), accessResult, reasonCode, access.accessTicket(), access.ticketExpiresAt(), access.consumptionMode(),
                access.traceId(), access.consumedAt());
        decryptAccesses.put(updated.decryptAccessId(), updated);
        return updated;
    }

    private Map<String, Object> auditDecryptAccessLifecycle(DecryptAccessState access, String eventType, String traceId) {
        Map<String, Object> audit = encryptionAudit(eventType, "SUCCESS", access.securityBindingId(), access.documentAssetId(), access.documentVersionId(),
                access.contractId(), access.accessSubjectType(), access.accessSubjectId(), access.actorDepartmentId(), access.decryptAccessId(), traceId);
        requireDocumentAsset(access.documentAssetId()).auditRecords().add(audit);
        return audit;
    }

    private Map<String, Object> encryptionAudit(String eventType, String eventResult, String securityBindingId, String documentAssetId,
                                                 String documentVersionId, String contractId, String actorType, String actorId,
                                                 String actorDepartmentId, String relatedResourceId, String traceId) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("audit_event_id", "ed-audit-" + UUID.randomUUID());
        audit.put("event_type", eventType);
        audit.put("event_result", eventResult);
        audit.put("security_binding_id", securityBindingId);
        audit.put("document_asset_id", documentAssetId);
        audit.put("document_version_id", documentVersionId);
        audit.put("contract_id", contractId);
        audit.put("actor_type", actorType);
        audit.put("actor_id", actorId);
        audit.put("actor_department_id", actorDepartmentId);
        audit.put("related_resource_type", relatedResourceId == null ? null : "ENCRYPTED_DOCUMENT_RUNTIME");
        audit.put("related_resource_id", relatedResourceId);
        audit.put("trace_id", traceId);
        audit.put("occurred_at", Instant.now().toString());
        encryptedDocumentAuditRecords.add(audit);
        return audit;
    }

    private AuthorizationDecision evaluateDownloadAuthorization(Map<String, Object> request, DocumentAssetState asset, String traceId, boolean writeAudit) {
        String userId = text(request, "requester_user_id", text(request, "requested_by", null));
        String departmentId = text(request, "requester_department_id", text(request, "requested_department_id", null));
        String documentVersionId = text(request, "document_version_id", asset.currentVersionId());
        EncryptionSecurityBindingState binding = requireEncryptionBindingByDocumentAsset(asset.documentAssetId());
        DownloadAuthorizationState matched = downloadAuthorizations.values().stream()
                .filter(this::isDownloadAuthorizationActiveNow)
                .filter(auth -> authorizationSubjectMatches(auth, userId, departmentId))
                .filter(auth -> authorizationScopeMatches(auth, asset))
                .sorted((left, right) -> {
                    int priority = Integer.compare(right.priorityNo(), left.priorityNo());
                    if (priority != 0) {
                        return priority;
                    }
                    return Integer.compare(subjectRank(right.subjectType()), subjectRank(left.subjectType()));
                })
                .findFirst()
                .orElse(null);
        Map<String, Object> audit = encryptionAudit(matched == null ? "DOWNLOAD_AUTH_DENIED" : "DOWNLOAD_AUTH_HIT", matched == null ? "REJECTED" : "SUCCESS",
                binding.securityBindingId(), asset.documentAssetId(), documentVersionId, asset.ownerId(), "USER", userId, departmentId,
                matched == null ? null : matched.authorizationId(), traceId);
        if (writeAudit) {
            asset.auditRecords().add(audit);
        }
        return new AuthorizationDecision(matched, matched == null ? null : authorizationSnapshot(matched, asset, userId, departmentId, documentVersionId), audit);
    }

    private boolean isDownloadAuthorizationActiveNow(DownloadAuthorizationState authorization) {
        Instant now = Instant.now();
        return "ACTIVE".equals(authorization.authorizationStatus())
                && !now.isBefore(Instant.parse(authorization.effectiveStartAt()))
                && !now.isAfter(Instant.parse(authorization.effectiveEndAt()));
    }

    private boolean authorizationSubjectMatches(DownloadAuthorizationState authorization, String userId, String departmentId) {
        return switch (authorization.subjectType()) {
            case "USER" -> authorization.subjectId().equals(userId);
            case "DEPARTMENT" -> authorization.subjectId().equals(departmentId);
            default -> false;
        };
    }

    private boolean authorizationScopeMatches(DownloadAuthorizationState authorization, DocumentAssetState asset) {
        return switch (authorization.scopeType()) {
            case "GLOBAL" -> "*".equals(authorization.scopeValue());
            case "CONTRACT" -> authorization.scopeValue().equals(asset.ownerId());
            case "DOCUMENT_ROLE" -> authorization.scopeValue().equals(asset.documentRole());
            case "ORG_SCOPE" -> authorization.scopeValue().equals(asset.ownerId());
            default -> false;
        };
    }

    private int subjectRank(String subjectType) {
        return "USER".equals(subjectType) ? 2 : 1;
    }

    private Map<String, Object> authorizationPolicySnapshot(Map<String, Object> request) {
        String scopeType = text(request, "scope_type", "GLOBAL");
        String scopeValue = text(request, "scope_value", "*");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scope_expression", Map.of(
                "expression_code", scopeType + ":" + scopeValue,
                "scope_type", scopeType,
                "scope_value", scopeValue,
                "expression_version", 1));
        snapshot.put("download_reason_required", bool(request, "download_reason_required", true));
        return snapshot;
    }

    private Map<String, Object> authorizationSnapshot(DownloadAuthorizationState authorization, DocumentAssetState asset,
                                                      String userId, String departmentId, String documentVersionId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("authorization_id", authorization.authorizationId());
        snapshot.put("subject_type", authorization.subjectType());
        snapshot.put("subject_id", authorization.subjectId());
        snapshot.put("requester_user_id", userId);
        snapshot.put("requester_department_id", departmentId);
        snapshot.put("document_asset_id", asset.documentAssetId());
        snapshot.put("document_version_id", documentVersionId);
        snapshot.put("contract_id", asset.ownerId());
        snapshot.put("scope_type", authorization.scopeType());
        snapshot.put("scope_value", authorization.scopeValue());
        snapshot.put("priority_no", authorization.priorityNo());
        snapshot.put("policy_snapshot", authorization.policySnapshot());
        snapshot.put("frozen_at", Instant.now().toString());
        return snapshot;
    }

    private Map<String, Object> downloadAuthorizationBody(DownloadAuthorizationState authorization, Map<String, Object> audit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authorization_id", authorization.authorizationId());
        body.put("authorization_name", authorization.authorizationName());
        body.put("authorization_status", authorization.authorizationStatus());
        body.put("subject_type", authorization.subjectType());
        body.put("subject_id", authorization.subjectId());
        body.put("scope_type", authorization.scopeType());
        body.put("scope_value", authorization.scopeValue());
        body.put("download_reason_required", authorization.downloadReasonRequired());
        body.put("effective_start_at", authorization.effectiveStartAt());
        body.put("effective_end_at", authorization.effectiveEndAt());
        body.put("priority_no", authorization.priorityNo());
        body.put("granted_by", authorization.grantedBy());
        body.put("revoked_by", authorization.revokedBy());
        body.put("revoked_at", authorization.revokedAt());
        body.put("policy_snapshot", authorization.policySnapshot());
        body.put("audit_event", audit);
        return body;
    }

    private Map<String, Object> downloadAuthorizationDecisionBody(AuthorizationDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", "ALLOWED");
        body.put("reason_code", "DOWNLOAD_AUTHORIZATION_MATCHED");
        body.put("matched_authorization", downloadAuthorizationBody(decision.authorization(), null));
        body.put("authorization_snapshot", decision.snapshot());
        body.put("explanation", Map.of(
                "subject_type", decision.authorization().subjectType(),
                "scope_type", decision.authorization().scopeType(),
                "priority_no", decision.authorization().priorityNo(),
                "matched_by", decision.authorization().subjectType() + "+" + decision.authorization().scopeType()));
        body.put("audit_event", decision.auditEvent());
        return body;
    }

    private Map<String, Object> authorizationAudit(DownloadAuthorizationState authorization, String eventType, String result, String traceId) {
        Map<String, Object> audit = encryptionAudit(eventType, result, null, null, null, contractIdForAuthorizationScope(authorization), "USER",
                authorization.grantedBy() == null ? authorization.revokedBy() : authorization.grantedBy(), null, authorization.authorizationId(), traceId);
        audit.put("subject_type", authorization.subjectType());
        audit.put("subject_id", authorization.subjectId());
        audit.put("scope_type", authorization.scopeType());
        audit.put("scope_value", authorization.scopeValue());
        return audit;
    }

    private String contractIdForAuthorizationScope(DownloadAuthorizationState authorization) {
        return "CONTRACT".equals(authorization.scopeType()) ? authorization.scopeValue() : null;
    }

    private void appendAuthorizationAuditToScopedAssets(DownloadAuthorizationState authorization, Map<String, Object> audit) {
        if (!"CONTRACT".equals(authorization.scopeType())) {
            return;
        }
        documentAssets.values().stream()
                .filter(asset -> authorization.scopeValue().equals(asset.ownerId()))
                .forEach(asset -> asset.auditRecords().add(new LinkedHashMap<>(audit)));
    }

    private Map<String, Object> decryptDownloadJobBody(DecryptDownloadJobState job, Map<String, Object> audit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decrypt_download_job_id", job.decryptDownloadJobId());
        body.put("security_binding_id", job.securityBindingId());
        body.put("authorization_id", job.authorizationId());
        body.put("document_asset_id", job.documentAssetId());
        body.put("document_version_id", job.documentVersionId());
        body.put("contract_id", job.contractId());
        body.put("requested_by", job.requestedBy());
        body.put("requested_department_id", job.requestedDepartmentId());
        body.put("download_reason", job.downloadReason());
        body.put("job_status", job.jobStatus());
        body.put("authorization_snapshot", job.authorizationSnapshot());
        body.put("export_artifact_ref", job.exportArtifactRef());
        body.put("export_file_name", job.exportFileName());
        body.put("download_url_token", job.downloadUrlToken());
        body.put("download_expires_at", job.downloadExpiresAt());
        body.put("result_code", job.resultCode());
        body.put("result_message", job.resultMessage());
        body.put("platform_job_ref", Map.of("platform_job_id", job.platformJobId(), "job_type", "ED_DECRYPT_DOWNLOAD_EXPORT", "job_status", job.jobStatus()));
        body.put("download_url", Map.of("token", job.downloadUrlToken(), "expires_at", job.downloadExpiresAt()));
        body.put("export_artifact", Map.of(
                "package_id", job.exportArtifactRef(),
                "artifact_status", "EXPIRED".equals(job.jobStatus()) ? "EXPIRED" : job.jobStatus(),
                "export_file_name", job.exportFileName(),
                "plaintext_detached_usable", true,
                "document_center_truth_replaced", false));
        body.put("audit_event", audit);
        return body;
    }

    private DecryptDownloadJobState updateDecryptDownloadJobStatus(DecryptDownloadJobState job, String status, String resultCode, String resultMessage) {
        DecryptDownloadJobState updated = new DecryptDownloadJobState(job.decryptDownloadJobId(), job.securityBindingId(), job.authorizationId(),
                job.documentAssetId(), job.documentVersionId(), job.contractId(), job.requestedBy(), job.requestedDepartmentId(), job.downloadReason(),
                job.requestIdempotencyKey(), status, job.authorizationSnapshot(), job.exportArtifactRef(), job.exportFileName(), job.downloadUrlToken(),
                job.downloadExpiresAt(), job.attemptCount(), job.platformJobId(), resultCode, resultMessage, job.requestedAt(), Instant.now().toString());
        decryptDownloadJobs.put(updated.decryptDownloadJobId(), updated);
        return updated;
    }

    private ResponseEntity<Map<String, Object>> rejectDownloadJobTransitionUnlessReady(DecryptDownloadJobState job, String targetStatus) {
        if ("READY".equals(job.jobStatus())) {
            return null;
        }
        Map<String, Object> body = error("DOWNLOAD_JOB_STATUS_CONFLICT", "仅 READY 下载作业允许流转到 " + targetStatus);
        body.put("current_job_status", job.jobStatus());
        body.put("requested_job_status", targetStatus);
        body.put("decrypt_download_job_id", job.decryptDownloadJobId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private Map<String, Object> auditDownloadJob(DecryptDownloadJobState job, String eventType, String eventResult, String traceId) {
        Map<String, Object> audit = encryptionAudit(eventType, eventResult, job.securityBindingId(), job.documentAssetId(), job.documentVersionId(),
                job.contractId(), "USER", job.requestedBy(), job.requestedDepartmentId(), job.decryptDownloadJobId(), traceId);
        requireDocumentAsset(job.documentAssetId()).auditRecords().add(audit);
        return audit;
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
        body.put("attachment_summaries", contract.attachments().stream().map(this::documentBody).toList());
        body.put("approval_summary", contract.approvalSummary());
        body.put("timeline_event", contract.events());
        body.put("audit_record", contract.events());
        body.put("signature_summary", signatureSummaries.get(contract.contractId()));
        body.put("performance_summary", performanceSummaries.get(contract.contractId()));
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
        body.put("approval_mode", process.approvalMode());
        body.put("oa_instance_id", process.oaInstanceId());
        body.put("approval_summary", process.approvalSummary());
        return body;
    }

    private Map<String, Object> callbackBody(String callbackResult, Map<String, Object> approvalSummary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_result", callbackResult);
        body.put("approval_summary", approvalSummary);
        return body;
    }

    private Map<String, Object> compensationTaskBody(CompensationTaskState task) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", task.taskId());
        body.put("task_type", task.taskType());
        body.put("contract_id", task.contractId());
        body.put("process_id", task.processId());
        body.put("compensation_status", task.compensationStatus());
        body.put("trace_id", task.traceId());
        body.put("created_at", task.createdAt());
        return body;
    }

    private Map<String, Object> documentBody(DocumentRef document) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_asset_id", document.documentAssetId());
        body.put("document_version_id", document.documentVersionId());
        body.put("effective_document_version_id", document.effectiveDocumentVersionId());
        body.put("document_role", document.documentRole());
        body.put("document_title", document.documentTitle());
        body.put("latest_version_no", document.latestVersionNo());
        body.put("document_status", document.documentStatus());
        return body;
    }

    private Map<String, Object> documentAssetBody(DocumentAssetState asset) {
        Map<String, Object> bindingSummary = new LinkedHashMap<>();
        bindingSummary.put("owner_type", asset.ownerType());
        bindingSummary.put("owner_id", asset.ownerId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_asset_id", asset.documentAssetId());
        body.put("current_version_id", asset.currentVersionId());
        body.put("owner_type", asset.ownerType());
        body.put("owner_id", asset.ownerId());
        body.put("document_role", asset.documentRole());
        body.put("document_title", asset.documentTitle());
        body.put("latest_version_no", asset.latestVersionNo());
        body.put("document_status", asset.documentStatus());
        body.put("encryption_status", asset.encryptionStatus());
        body.put("preview_status", asset.previewStatus());
        body.put("binding_summary", bindingSummary);
        String securityBindingId = encryptionBindingByDocumentAsset.get(asset.documentAssetId());
        if (securityBindingId != null) {
            body.put("security_binding_summary", securityBindingBody(requireEncryptionBinding(securityBindingId)));
        }
        body.put("audit_record", asset.auditRecords());
        return body;
    }

    private Map<String, Object> documentVersionBody(DocumentVersionState version) {
        DocumentAssetState asset = requireDocumentAsset(version.documentAssetId());
        boolean current = version.documentVersionId().equals(asset.currentVersionId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_version_id", version.documentVersionId());
        body.put("document_asset_id", version.documentAssetId());
        body.put("version_no", version.versionNo());
        body.put("parent_version_id", version.parentVersionId());
        body.put("base_version_id", version.baseVersionId());
        body.put("version_label", version.versionLabel());
        body.put("change_reason", version.changeReason());
        body.put("version_status", current ? "ACTIVE" : "SUPERSEDED");
        body.put("is_current_version", current);
        body.put("preview_generation_status", "NOT_STARTED");
        return body;
    }

    private Map<String, Object> processDefinitionBody(ProcessDefinitionState definition) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definition_id", definition.definitionId());
        body.put("process_code", definition.processCode());
        body.put("process_name", definition.processName());
        body.put("business_type", definition.businessType());
        body.put("approval_mode", definition.approvalMode());
        body.put("definition_status", definition.definitionStatus());
        body.put("organization_binding_required", definition.organizationBindingRequired());
        body.put("current_draft_version", processVersionBody(requireProcessVersion(definition.currentDraftVersionId())));
        body.put("latest_published_version", definition.latestPublishedVersionId() == null ? null : processVersionBody(requireProcessVersion(definition.latestPublishedVersionId())));
        body.put("audit_record", definition.auditRecords());
        return body;
    }

    private Map<String, Object> processVersionBody(ProcessVersionState version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version_id", version.versionId());
        body.put("definition_id", version.definitionId());
        body.put("version_no", version.versionNo());
        body.put("version_status", version.versionStatus());
        body.put("version_snapshot", version.versionSnapshot());
        body.put("published_at", version.publishedAt());
        body.put("published_by", version.publishedBy());
        body.put("version_note", version.versionNote());
        return body;
    }

    private Map<String, Object> workflowProcessBody(WorkflowProcessState process, List<Map<String, Object>> tasks) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!tasks.isEmpty()) {
            body.put("task_id", tasks.getFirst().get("task_id"));
        }
        body.put("process_id", process.processId());
        body.put("definition_id", process.definitionId());
        body.put("version_id", process.versionId());
        body.put("contract_id", process.contractId());
        body.put("approval_mode", process.approvalMode());
        body.put("instance_status", process.instanceStatus());
        body.put("current_node_key", process.currentNodeKey());
        body.put("task_center_items", tasks);
        body.put("approval_summary", workflowApprovalSummary(process));
        body.put("participant_snapshot", tasks.isEmpty() ? List.of() : tasks.stream().map(task -> task.get("resolver_snapshot")).toList());
        return body;
    }

    private Map<String, Object> approvalTaskBody(ApprovalTaskState task) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", task.taskId());
        body.put("process_id", task.processId());
        body.put("task_status", task.taskStatus());
        body.put("task_type", task.taskType());
        body.put("node_key", task.nodeKey());
        body.put("assignee_user_id", task.assigneeUserId());
        body.put("assignee_org_unit_id", task.assigneeOrgUnitId());
        body.put("candidate_list", task.candidateList());
        body.put("resolver_snapshot", task.resolverSnapshot());
        body.put("task_center_ref", task.taskCenterRef());
        body.put("available_action_list", List.of("APPROVE", "REJECT", "TERMINATE"));
        return body;
    }

    private Map<String, Object> workflowApprovalSummary(WorkflowProcessState process) {
        Map<String, Object> latestAction = process.approvalActions().isEmpty() ? null : process.approvalActions().getLast();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("process_id", process.processId());
        summary.put("contract_id", process.contractId());
        summary.put("approval_mode", process.approvalMode());
        summary.put("summary_status", process.instanceStatus());
        summary.put("current_node_name", process.currentNodeKey());
        summary.put("current_approver_list", pendingTasks(process.processId()).stream().map(task -> task.get("assignee_user_id")).toList());
        summary.put("latest_action", latestAction);
        summary.put("started_at", process.startedAt());
        summary.put("finished_at", process.finishedAt());
        return summary;
    }

    private List<Map<String, Object>> missingApprovalNodeBindings(Map<String, Object> snapshot) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (Map<String, Object> node : nodes(snapshot)) {
            if (!"APPROVAL".equals(text(node, "node_type", null))) {
                continue;
            }
            Object bindings = node.get("bindings");
            if (!(bindings instanceof List<?> list) || list.isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("node_key", text(node, "node_key", null));
                error.put("error_code", "WORKFLOW_NODE_BINDING_REQUIRED");
                error.put("message", "审批节点缺少组织绑定");
                errors.add(error);
                continue;
            }
            for (Map<String, Object> binding : bindings(node)) {
                if ("ORG_RULE".equals(text(binding, "binding_type", null))
                        && text(binding, "binding_object_id", "").startsWith("missing-")) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("node_key", text(node, "node_key", null));
                    error.put("binding_object_id", text(binding, "binding_object_id", null));
                    error.put("error_code", "ORG_NODE_RESOLUTION_FAILED");
                    error.put("message", "组织规则未解析到有效审批人");
                    errors.add(error);
                }
            }
        }
        return errors;
    }

    private void copyApprovalFlowRefs(Map<String, Object> target, Map<String, Object> source) {
        copyIfPresent(target, source, "process_definition_id");
        copyIfPresent(target, source, "process_version_id");
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value != null && !value.toString().isBlank()) {
            target.put(field, value);
        }
    }

    private ApprovalTaskState createApprovalTask(WorkflowProcessState process, Map<String, Object> node, Map<String, Object> binding,
                                                 String participantMode, int sequenceIndex) {
        String taskId = "wf-task-" + UUID.randomUUID();
        String bindingType = text(binding, "binding_type", null);
        String bindingObjectId = text(binding, "binding_object_id", null);
        List<Map<String, Object>> candidateList = List.of(candidate(bindingType, bindingObjectId, text(binding, "binding_object_name", null)));
        Map<String, Object> resolverSnapshot = new LinkedHashMap<>();
        resolverSnapshot.put("snapshot_id", "wf-snapshot-" + UUID.randomUUID());
        resolverSnapshot.put("binding_type", bindingType);
        resolverSnapshot.put("binding_object_id", bindingObjectId);
        resolverSnapshot.put("resolution_status", "RESOLVED");
        resolverSnapshot.put("resolved_assignee_list", candidateList);

        Map<String, Object> taskCenterRef = new LinkedHashMap<>();
        taskCenterRef.put("task_center_task_id", "task-center-" + taskId);
        taskCenterRef.put("task_center_status", "PUBLISHED");
        return new ApprovalTaskState(taskId, process.processId(), text(node, "node_key", null), "COUNTERSIGN".equals(participantMode) ? "COUNTERSIGN" : "APPROVAL",
                "PENDING_ACTION", "USER".equals(bindingType) ? bindingObjectId : null, "ORG_UNIT".equals(bindingType) ? bindingObjectId : null,
                candidateList, resolverSnapshot, sequenceIndex, participantMode, null, taskCenterRef);
    }

    private Map<String, Object> approvalAction(String actionId, String processId, String taskId, String actionType, String operatorUserId, String comment) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("approval_action_id", actionId);
        action.put("process_id", processId);
        action.put("task_id", taskId);
        action.put("action_type", actionType);
        action.put("operator_user_id", operatorUserId);
        action.put("comment", comment);
        action.put("action_result", "ACCEPTED");
        action.put("acted_at", Instant.now().toString());
        return action;
    }

    private Map<String, Object> candidate(String bindingType, String bindingObjectId, String bindingObjectName) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("binding_type", bindingType);
        candidate.put("binding_object_id", bindingObjectId);
        candidate.put("binding_object_name", bindingObjectName);
        candidate.put("user_id", "USER".equals(bindingType) ? bindingObjectId : "resolved-" + bindingObjectId);
        return candidate;
    }

    private Map<String, Object> firstApprovalNode(Map<String, Object> snapshot) {
        return nodes(snapshot).stream()
                .filter(node -> "APPROVAL".equals(text(node, "node_type", null)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程版本缺少审批节点"));
    }

    private List<Map<String, Object>> pendingTasks(String processId) {
        return approvalTasks.values().stream()
                .filter(task -> processId.equals(task.processId()))
                .filter(task -> "PENDING_ACTION".equals(task.taskStatus()))
                .map(this::approvalTaskBody)
                .toList();
    }

    private boolean hasNextSerialTask(WorkflowProcessState process, ApprovalTaskState task) {
        ProcessVersionState version = requireProcessVersion(process.versionId());
        return task.sequenceIndex() + 1 < bindings(firstApprovalNode(version.versionSnapshot())).size();
    }

    private void cancelPendingTasks(String processId) {
        approvalTasks.values().stream()
                .filter(task -> processId.equals(task.processId()))
                .filter(task -> "PENDING_ACTION".equals(task.taskStatus()))
                .toList()
                .forEach(task -> approvalTasks.put(task.taskId(), new ApprovalTaskState(task.taskId(), task.processId(), task.nodeKey(), task.taskType(), "CANCELED",
                        task.assigneeUserId(), task.assigneeOrgUnitId(), task.candidateList(), task.resolverSnapshot(), task.sequenceIndex(),
                        task.participantMode(), Instant.now().toString(), task.taskCenterRef())));
    }

    private boolean isTerminal(String status) {
        return List.of("COMPLETED", "REJECTED", "TERMINATED").contains(status);
    }

    private Map<String, Object> approvalSummary(String processId, String processStatus, String result) {
        return approvalSummary(processId, processStatus, result, null, "HEALTHY");
    }

    private Map<String, Object> approvalSummary(String processId, String processStatus, String result, String approvalMode, String compensationStatus) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("process_id", processId);
        summary.put("process_status", processStatus);
        summary.put("summary_status", processStatus);
        summary.put("result", result);
        summary.put("final_result", result);
        summary.put("approval_mode", approvalMode);
        Map<String, Object> bridgeHealth = new LinkedHashMap<>();
        bridgeHealth.put("compensation_status", compensationStatus);
        summary.put("bridge_health", bridgeHealth);
        return summary;
    }

    private void writeBackContractApproval(ProcessState process, String processStatus, String traceId) {
        ContractState contract = requireContract(process.contractId());
        String contractStatus = switch (processStatus) {
            case "COMPLETED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "TERMINATED" -> "APPROVAL_TERMINATED";
            default -> contract.contractStatus();
        };
        ContractState updatedContract = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contractStatus,
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                copyDocuments(contract.attachments()), process.approvalSummary(), process.processId(), copyEvents(contract.events()));
        updatedContract.events().add(event(eventType(processStatus), process.processId(), traceId));
        contracts.put(contract.contractId(), updatedContract);
    }

    private void createCompensationTask(ProcessState process, String traceId) {
        String taskId = "cmp-task-" + UUID.randomUUID();
        compensationTasks.put(taskId, new CompensationTaskState(taskId, "CONTRACT_APPROVAL_WRITEBACK", process.contractId(), process.processId(),
                "PENDING_RETRY", traceId, Instant.now().toString()));
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
            case "DOCUMENT_ASSET_CREATED" -> "文档主档已创建并绑定业务对象";
            case "DOCUMENT_BOUND" -> "文档已绑定合同当前主版本";
            case "ATTACHMENT_BOUND" -> "附件已挂接合同并刷新附件摘要";
            case "DOCUMENT_VERSION_APPENDED" -> "文档版本已追加并刷新当前主版本";
            case "DOCUMENT_VERSION_ACTIVATED" -> "文档当前主版本已切换";
            case "DOCUMENT_MAIN_VERSION_SWITCHED" -> "合同主正文业务有效版本已切换";
            case "APPROVAL_STARTED" -> "审批已发起并回写合同状态";
            case "APPROVAL_APPROVED" -> "审批通过并回写合同状态";
            case "APPROVAL_REJECTED" -> "审批驳回并回写合同状态";
            case "APPROVAL_TERMINATED" -> "审批终止并回写合同状态";
            case "PERFORMANCE_STARTED" -> "履约已启动并写入生命周期摘要";
            case "PERFORMANCE_NODE_CREATED" -> "履约节点已创建";
            case "PERFORMANCE_PROGRESS_UPDATED" -> "履约进展已更新";
            case "PERFORMANCE_NODE_OVERDUE" -> "履约节点已逾期";
            case "PERFORMANCE_RISK_CHANGED" -> "履约风险已变化";
            case "PERFORMANCE_COMPLETED" -> "履约已完成并回写合同摘要";
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

    private PerformanceRecordState requirePerformanceRecord(String performanceRecordId) {
        PerformanceRecordState record = performanceRecords.get(performanceRecordId);
        if (record == null) {
            throw new IllegalArgumentException("performance_record_id 不存在: " + performanceRecordId);
        }
        return record;
    }

    private PerformanceNodeState requirePerformanceNode(String performanceNodeId) {
        PerformanceNodeState node = performanceNodes.get(performanceNodeId);
        if (node == null) {
            throw new IllegalArgumentException("performance_node_id 不存在: " + performanceNodeId);
        }
        return node;
    }

    private SignatureRequestState requireSignatureRequest(String signatureRequestId) {
        SignatureRequestState state = signatureRequests.get(signatureRequestId);
        if (state == null) {
            throw new IllegalArgumentException("signature_request_id 不存在: " + signatureRequestId);
        }
        return state;
    }

    private SignatureSessionState requireSignatureSession(String signatureSessionId) {
        SignatureSessionState state = signatureSessions.get(signatureSessionId);
        if (state == null) {
            throw new IllegalArgumentException("signature_session_id 不存在: " + signatureSessionId);
        }
        return state;
    }

    private DocumentAssetState requireDocumentAsset(String documentAssetId) {
        DocumentAssetState asset = documentAssets.get(documentAssetId);
        if (asset == null) {
            throw new IllegalArgumentException("document_asset_id 不存在: " + documentAssetId);
        }
        return asset;
    }

    private EncryptionSecurityBindingState requireEncryptionBinding(String securityBindingId) {
        EncryptionSecurityBindingState binding = encryptionSecurityBindings.get(securityBindingId);
        if (binding == null) {
            throw new IllegalArgumentException("security_binding_id 不存在: " + securityBindingId);
        }
        return binding;
    }

    private EncryptionSecurityBindingState requireEncryptionBindingByDocumentAsset(String documentAssetId) {
        String bindingId = encryptionBindingByDocumentAsset.get(documentAssetId);
        if (bindingId == null) {
            throw new IllegalArgumentException("document_asset_id 未纳入加密治理: " + documentAssetId);
        }
        return requireEncryptionBinding(bindingId);
    }

    private EncryptionCheckInState requireEncryptionCheckIn(String checkInId) {
        EncryptionCheckInState checkIn = encryptionCheckIns.get(checkInId);
        if (checkIn == null) {
            throw new IllegalArgumentException("check_in_id 不存在: " + checkInId);
        }
        return checkIn;
    }

    private DecryptAccessState requireDecryptAccess(String decryptAccessId) {
        DecryptAccessState access = decryptAccesses.get(decryptAccessId);
        if (access == null) {
            throw new IllegalArgumentException("decrypt_access_id 不存在: " + decryptAccessId);
        }
        return access;
    }

    private DownloadAuthorizationState requireDownloadAuthorization(String authorizationId) {
        DownloadAuthorizationState authorization = downloadAuthorizations.get(authorizationId);
        if (authorization == null) {
            throw new IllegalArgumentException("authorization_id 不存在: " + authorizationId);
        }
        return authorization;
    }

    private DecryptDownloadJobState requireDecryptDownloadJob(String jobId) {
        DecryptDownloadJobState job = decryptDownloadJobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("decrypt_download_job_id 不存在: " + jobId);
        }
        return job;
    }

    private DocumentVersionState requireDocumentVersion(String documentVersionId) {
        DocumentVersionState version = documentVersions.get(documentVersionId);
        if (version == null) {
            throw new IllegalArgumentException("document_version_id 不存在: " + documentVersionId);
        }
        return version;
    }

    private void refreshContractDocumentRef(DocumentAssetState asset, String traceId) {
        if (!"CONTRACT".equals(asset.ownerType())) {
            return;
        }
        ContractState contract = requireContract(asset.ownerId());
        DocumentRef document = documentRef(asset);
        List<DocumentRef> attachments = copyDocuments(contract.attachments());
        DocumentRef currentDocument = contract.currentDocument();
        if ("MAIN_BODY".equals(asset.documentRole())) {
            currentDocument = document;
        } else {
            attachments.removeIf(ref -> asset.documentAssetId().equals(ref.documentAssetId()));
            attachments.add(document);
        }
        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contract.contractStatus(),
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), currentDocument, attachments,
                contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event("DOCUMENT_MAIN_VERSION_SWITCHED", asset.currentVersionId(), traceId));
        contracts.put(contract.contractId(), updated);
    }

    private void bindContractDocument(ContractState contract, DocumentAssetState asset, String traceId) {
        DocumentRef document = documentRef(asset);
        List<DocumentRef> attachments = copyDocuments(contract.attachments());
        DocumentRef currentDocument = contract.currentDocument();
        String eventType;
        if ("MAIN_BODY".equals(asset.documentRole())) {
            currentDocument = document;
            eventType = "DOCUMENT_BOUND";
        } else {
            attachments.add(document);
            eventType = "ATTACHMENT_BOUND";
        }
        ContractState updated = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contract.contractStatus(),
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), currentDocument, attachments,
                contract.approvalSummary(), contract.processId(), copyEvents(contract.events()));
        updated.events().add(event(eventType, document.documentAssetId(), traceId));
        contracts.put(contract.contractId(), updated);
    }

    private DocumentRef documentRef(DocumentAssetState asset) {
        return new DocumentRef(asset.documentAssetId(), asset.currentVersionId(), asset.currentVersionId(), asset.documentRole(), asset.documentTitle(),
                asset.latestVersionNo(), asset.documentStatus());
    }

    private String normalizeDocumentRole(String role) {
        return "CONTRACT_BODY".equals(role) ? "MAIN_BODY" : role;
    }

    private ProcessState requireProcess(String processId) {
        ProcessState process = processes.get(processId);
        if (process == null) {
            throw new IllegalArgumentException("process_id 不存在: " + processId);
        }
        return process;
    }

    private ProcessDefinitionState requireProcessDefinition(String definitionId) {
        ProcessDefinitionState definition = processDefinitions.get(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("definition_id 不存在: " + definitionId);
        }
        return definition;
    }

    private ProcessVersionState requireProcessVersion(String versionId) {
        ProcessVersionState version = processVersions.get(versionId);
        if (version == null) {
            throw new IllegalArgumentException("version_id 不存在: " + versionId);
        }
        return version;
    }

    private WorkflowProcessState requireWorkflowProcess(String processId) {
        WorkflowProcessState process = workflowProcesses.get(processId);
        if (process == null) {
            throw new IllegalArgumentException("process_id 不存在: " + processId);
        }
        return process;
    }

    private ApprovalTaskState requireApprovalTask(String taskId) {
        ApprovalTaskState task = approvalTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task_id 不存在: " + taskId);
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? new LinkedHashMap<>((Map<String, Object>) source) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nodes(Map<String, Object> snapshot) {
        Object nodes = snapshot.get("nodes");
        return nodes instanceof List<?> source ? (List<Map<String, Object>>) source : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listMaps(Object value) {
        return value instanceof List<?> source ? (List<Map<String, Object>>) source : List.of();
    }

    private List<Map<String, Object>> copyAssignmentList(List<Map<String, Object>> assignments) {
        return assignments.stream().map(LinkedHashMap::new).map(item -> (Map<String, Object>) item).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> bindings(Map<String, Object> node) {
        Object bindings = node.get("bindings");
        return bindings instanceof List<?> source ? (List<Map<String, Object>>) source : List.of();
    }

    private boolean bool(Map<String, Object> request, String field, boolean defaultValue) {
        Object value = request.get(field);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private List<Map<String, Object>> copyEvents(List<Map<String, Object>> events) {
        return new ArrayList<>(events);
    }

    private List<DocumentRef> copyDocuments(List<DocumentRef> documents) {
        return new ArrayList<>(documents);
    }

    private List<String> copyCallbackEventIds(List<String> callbackEventIds) {
        return new ArrayList<>(callbackEventIds);
    }

    private int intValue(Map<String, Object> request, String field, int defaultValue) {
        Object value = request.get(field);
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }

    private String text(Map<String, Object> request, String field, String defaultValue) {
        Object value = request.get(field);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private record ContractState(String contractId, String contractNo, String contractName, String contractStatus,
                                  String ownerOrgUnitId, String ownerUserId, String amount, String currency,
                                  DocumentRef currentDocument, List<DocumentRef> attachments,
                                  Map<String, Object> approvalSummary, String processId,
                                  List<Map<String, Object>> events) {
    }

    private record DocumentRef(String documentAssetId, String documentVersionId, String effectiveDocumentVersionId,
                               String documentRole, String documentTitle, int latestVersionNo, String documentStatus) {
    }

    private record DocumentAssetState(String documentAssetId, String currentVersionId, String ownerType, String ownerId,
                                      String documentRole, String documentTitle, String documentStatus, String encryptionStatus,
                                      String previewStatus, int latestVersionNo, List<Map<String, Object>> auditRecords) {
    }

    private record DocumentVersionState(String documentVersionId, String documentAssetId, int versionNo, String parentVersionId,
                                        String baseVersionId, String versionLabel, String changeReason, String versionStatus,
                                        String fileUploadToken, String sourceChannel) {
    }

    private record ProcessDefinitionState(String definitionId, String processCode, String processName, String businessType,
                                          String approvalMode, String definitionStatus, String currentDraftVersionId,
                                          String latestPublishedVersionId, boolean organizationBindingRequired,
                                          List<Map<String, Object>> auditRecords) {
    }

    private record ProcessVersionState(String versionId, String definitionId, int versionNo, String versionStatus,
                                       Map<String, Object> versionSnapshot, String publishedAt, String publishedBy,
                                       String createdBy, String versionNote) {
    }

    private record WorkflowProcessState(String processId, String definitionId, String versionId, String contractId,
                                        String approvalMode, String instanceStatus, String currentNodeKey,
                                        String starterUserId, String startedAt, String finishedAt,
                                        List<String> taskIds, List<Map<String, Object>> approvalActions) {
    }

    private record ApprovalTaskState(String taskId, String processId, String nodeKey, String taskType, String taskStatus,
                                     String assigneeUserId, String assigneeOrgUnitId, List<Map<String, Object>> candidateList,
                                     Map<String, Object> resolverSnapshot, int sequenceIndex, String participantMode,
                                     String completedAt, Map<String, Object> taskCenterRef) {
    }

    private record ProcessState(String processId, String contractId, String documentAssetId, String documentVersionId,
                                String processStatus, Map<String, Object> approvalSummary, String approvalMode,
                                String oaInstanceId, int lastEventSequence, List<String> callbackEventIds,
                                boolean simulateContractWritebackFailure) {
    }

    private record CompensationTaskState(String taskId, String taskType, String contractId, String processId,
                                          String compensationStatus, String traceId, String createdAt) {
    }

    private record SignatureRequestState(String signatureRequestId, String contractId, String requestStatus,
                                          String signatureStatus, String mainDocumentAssetId, String mainDocumentVersionId,
                                          String signatureMode, String sealSchemeId, String signOrderMode,
                                          String requestFingerprint, String idempotencyKey,
                                          Map<String, Object> applicationSnapshot, Map<String, Object> inputDocumentBinding,
                                          List<Map<String, Object>> auditRecords, String createdAt, String createdBy,
                                          String currentSessionId, String latestResultId) {
    }

    private record SignatureSessionState(String signatureSessionId, String signatureRequestId, String contractId,
                                         String sessionStatus, int sessionRound, String signOrderMode, int currentSignStep,
                                         int pendingSignerCount, int completedSignerCount, String startedAt, String expiresAt,
                                         String completedAt, String closeReason, String lastCallbackEventId,
                                         List<String> callbackEventIds, List<Map<String, Object>> assignments) {
    }

    private record SignatureResultState(String signatureResultId, String signatureRequestId, String signatureSessionId,
                                        String contractId, String resultStatus, String verificationStatus,
                                        String documentWritebackStatus, String contractWritebackStatus,
                                        String signedDocumentAssetId, String signedDocumentVersionId,
                                        String verificationDocumentAssetId, String verificationDocumentVersionId,
                                        String externalResultRef, String completedAt, List<Map<String, Object>> documentBindings,
                                        Map<String, Object> contractSignatureSummary) {
    }

    private record PaperRecordState(String paperRecordId, String contractId, String signatureRequestId, String recordStatus,
                                     String recordedSignDate, String paperDocumentAssetId, String paperDocumentVersionId,
                                     String confirmedBy, String confirmedAt, Map<String, Object> paperScanBinding,
                                     Map<String, Object> signatureSummary) {
    }

    private record PerformanceRecordState(String performanceRecordId, String contractId, String performanceStatus,
                                          int progressPercent, String riskLevel, String ownerUserId, String ownerOrgUnitId,
                                          int openNodeCount, int overdueNodeCount, String latestDueAt, String summaryText,
                                          String latestMilestoneCode, String lastEvaluatedAt, String lastWritebackAt) {
    }

    private record PerformanceNodeState(String performanceNodeId, String performanceRecordId, String contractId,
                                        String nodeType, String nodeName, String milestoneCode, String plannedAt,
                                        String dueAt, String actualAt, String nodeStatus, int progressPercent,
                                        String riskLevel, int issueCount, boolean overdue, String resultSummary,
                                        String lastResultAt, String ownerUserId, String ownerOrgUnitId,
                                        Map<String, Object> documentRef) {
    }

    private record EncryptionSecurityBindingState(String securityBindingId, String documentAssetId, String currentVersionId,
                                                  String contractId, String encryptionStatus, String internalAccessMode,
                                                  String downloadControlMode, String latestCheckInId,
                                                  String lastSuccessfulEncryptedVersionId, String lastSecurityEventAt,
                                                  int securityVersionNo) {
    }

    private record EncryptionCheckInState(String checkInId, String securityBindingId, String documentAssetId,
                                          String documentVersionId, String contractId, String triggerType,
                                          String checkInStatus, String encryptionResultStatus, String idempotencyKey,
                                          String resultCode, String resultMessage, String platformJobId,
                                          String acceptedAt, String completedAt) {
    }

    private record DecryptAccessState(String decryptAccessId, String securityBindingId, String documentAssetId,
                                       String documentVersionId, String contractId, String accessScene,
                                       String accessSubjectType, String accessSubjectId, String actorDepartmentId,
                                       String accessResult, String decisionReasonCode, String accessTicket,
                                       String ticketExpiresAt, String consumptionMode, String traceId,
                                       String consumedAt) {
    }

    private record DownloadAuthorizationState(String authorizationId, String authorizationName, String authorizationStatus,
                                              String subjectType, String subjectId, String scopeType, String scopeValue,
                                              boolean downloadReasonRequired, String effectiveStartAt, String effectiveEndAt,
                                              int priorityNo, String grantedBy, String revokedBy, String revokedAt,
                                              Map<String, Object> policySnapshot) {
    }

    private record DecryptDownloadJobState(String decryptDownloadJobId, String securityBindingId, String authorizationId,
                                           String documentAssetId, String documentVersionId, String contractId,
                                           String requestedBy, String requestedDepartmentId, String downloadReason,
                                           String requestIdempotencyKey, String jobStatus, Map<String, Object> authorizationSnapshot,
                                           String exportArtifactRef, String exportFileName, String downloadUrlToken,
                                           String downloadExpiresAt, int attemptCount, String platformJobId,
                                           String resultCode, String resultMessage, String requestedAt, String completedAt) {
    }

    private record AuthorizationDecision(DownloadAuthorizationState authorization, Map<String, Object> snapshot,
                                         Map<String, Object> auditEvent) {
    }
}
