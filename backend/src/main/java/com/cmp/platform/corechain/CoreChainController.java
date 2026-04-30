package com.cmp.platform.corechain;

import com.cmp.platform.agentos.AgentOsGateway;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @GetMapping("/api/contracts/{contractId}/batch4-consumption-summary")
    Map<String, Object> batch4ContractConsumptionSummary(@PathVariable String contractId) {
        return service.batch4ContractConsumptionSummary(contractId);
    }

    @GetMapping("/api/contracts/{contractId}/semantic-references")
    Map<String, Object> contractSemanticReferences(@PathVariable String contractId) {
        return service.contractSemanticReferences(contractId);
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

    @GetMapping("/api/document-center/assets/{documentAssetId}/capability-bindings")
    Map<String, Object> documentCapabilityBindings(@PathVariable String documentAssetId) {
        return service.documentCapabilityBindings(documentAssetId);
    }

    @GetMapping("/api/document-center/events")
    Map<String, Object> documentCenterEvents(@RequestParam(required = false) String consumer_scope) {
        return service.documentCenterEvents(consumer_scope);
    }

    @PostMapping("/api/intelligent-applications/ocr/jobs")
    ResponseEntity<Map<String, Object>> createOcrJob(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @RequestBody Map<String, Object> request) {
        return service.createOcrJob(permissions, idempotencyKey, request);
    }

    @GetMapping("/api/intelligent-applications/ocr/jobs/{ocrJobId}")
    Map<String, Object> ocrJob(@PathVariable String ocrJobId) {
        return service.ocrJob(ocrJobId);
    }

    @GetMapping("/api/intelligent-applications/ocr/results/{resultId}")
    Map<String, Object> ocrResult(@PathVariable String resultId) {
        return service.ocrResult(resultId);
    }

    @GetMapping("/api/intelligent-applications/ocr/results")
    Map<String, Object> ocrResults(@RequestParam(required = false) String document_asset_id,
                                    @RequestParam(required = false) String document_version_id,
                                    @RequestParam(required = false, defaultValue = "false") boolean default_only) {
        return service.ocrResults(document_asset_id, document_version_id, default_only);
    }

    @PostMapping("/api/intelligent-applications/search/sources/refresh")
    ResponseEntity<Map<String, Object>> refreshSearchSources(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                             @RequestBody Map<String, Object> request) {
        return service.refreshSearchSources(permissions, request);
    }

    @PostMapping("/api/intelligent-applications/search/query")
    ResponseEntity<Map<String, Object>> search(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                               @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope,
                                               @RequestHeader(value = "X-CMP-Deny-Objects", required = false) String deniedObjects,
                                               @RequestBody Map<String, Object> request) {
        return service.search(permissions, orgScope, deniedObjects, request);
    }

    @GetMapping("/api/intelligent-applications/search/snapshots/{resultSetId}")
    ResponseEntity<Map<String, Object>> searchSnapshot(@PathVariable String resultSetId,
                                                       @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                       @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope) {
        return service.searchSnapshot(resultSetId, permissions, orgScope);
    }

    @PostMapping("/api/intelligent-applications/search/snapshots/{resultSetId}/export")
    ResponseEntity<Map<String, Object>> exportSearchSnapshot(@PathVariable String resultSetId,
                                                             @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                             @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope,
                                                             @RequestBody(required = false) Map<String, Object> request) {
        return service.exportSearchSnapshot(resultSetId, permissions, orgScope, request == null ? Map.of() : request);
    }

    @PostMapping("/api/intelligent-applications/search/snapshots/{resultSetId}/replay")
    ResponseEntity<Map<String, Object>> replaySearchSnapshot(@PathVariable String resultSetId,
                                                             @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                             @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope,
                                                             @RequestBody(required = false) Map<String, Object> request) {
        return service.replaySearchSnapshot(resultSetId, permissions, orgScope, request == null ? Map.of() : request);
    }

    @PostMapping("/api/intelligent-applications/search/rebuilds")
    ResponseEntity<Map<String, Object>> rebuildSearchIndex(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                           @RequestBody Map<String, Object> request) {
        return service.rebuildSearchIndex(permissions, request);
    }

    @PostMapping("/api/intelligent-applications/search/permissions/changed")
    ResponseEntity<Map<String, Object>> expireSearchSnapshotsForPermissionChange(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                                  @RequestBody Map<String, Object> request) {
        return service.expireSearchSnapshotsForPermissionChange(permissions, request);
    }

    @PostMapping("/api/intelligent-applications/ai/jobs")
    ResponseEntity<Map<String, Object>> createAiApplicationJob(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                               @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                               @RequestBody Map<String, Object> request) {
        return service.createAiApplicationJob(permissions, orgScope, idempotencyKey, request);
    }

    @PostMapping("/api/intelligent-applications/ai/protected-results/{snapshotId}/guardrail-replay")
    ResponseEntity<Map<String, Object>> replayAiGuardrail(@PathVariable String snapshotId,
                                                           @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                           @RequestBody(required = false) Map<String, Object> request) {
        return service.replayAiGuardrail(snapshotId, permissions, request == null ? Map.of() : request);
    }

    @PostMapping("/api/intelligent-applications/candidates/ranking-jobs")
    ResponseEntity<Map<String, Object>> createCandidateRankingJob(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                  @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope,
                                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                  @RequestBody Map<String, Object> request) {
        return service.createCandidateRankingJob(permissions, orgScope, idempotencyKey, request);
    }

    @PostMapping("/api/intelligent-applications/candidates/profiles/switch")
    ResponseEntity<Map<String, Object>> switchCandidateProfiles(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                @RequestBody Map<String, Object> request) {
        return service.switchCandidateProfiles(permissions, request);
    }

    @PostMapping("/api/intelligent-applications/candidates/ranking-snapshots/{snapshotId}/replay")
    ResponseEntity<Map<String, Object>> replayCandidateRankingSnapshot(@PathVariable String snapshotId,
                                                                       @RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                       @RequestHeader(value = "X-CMP-Org-Scope", required = false) String orgScope,
                                                                       @RequestBody(required = false) Map<String, Object> request) {
        return service.replayCandidateRankingSnapshot(snapshotId, permissions, orgScope, request == null ? Map.of() : request);
    }

    @PostMapping("/api/intelligent-applications/candidates/writeback-candidates")
    ResponseEntity<Map<String, Object>> createCandidateWriteback(@RequestHeader(value = "X-CMP-Permissions", required = false) String permissions,
                                                                 @RequestBody Map<String, Object> request) {
        return service.createCandidateWriteback(permissions, request);
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

    @PostMapping("/api/contracts/{contractId}/changes")
    ResponseEntity<Map<String, Object>> createContractChange(@PathVariable String contractId,
                                                             @RequestBody Map<String, Object> request) {
        return service.createContractChange(contractId, request);
    }

    @PostMapping("/api/contracts/{contractId}/changes/{changeId}/approval-results")
    ResponseEntity<Map<String, Object>> applyContractChangeResult(@PathVariable String contractId,
                                                                  @PathVariable String changeId,
                                                                  @RequestBody Map<String, Object> request) {
        return service.applyContractChangeResult(contractId, changeId, request);
    }

    @PostMapping("/api/contracts/{contractId}/terminations")
    ResponseEntity<Map<String, Object>> createContractTermination(@PathVariable String contractId,
                                                                  @RequestBody Map<String, Object> request) {
        return service.createContractTermination(contractId, request);
    }

    @PostMapping("/api/contracts/{contractId}/terminations/{terminationId}/approval-results")
    ResponseEntity<Map<String, Object>> applyContractTerminationResult(@PathVariable String contractId,
                                                                       @PathVariable String terminationId,
                                                                       @RequestBody Map<String, Object> request) {
        return service.applyContractTerminationResult(contractId, terminationId, request);
    }

    @PostMapping("/api/contracts/{contractId}/archives")
    ResponseEntity<Map<String, Object>> createArchiveRecord(@PathVariable String contractId,
                                                            @RequestBody Map<String, Object> request) {
        return service.createArchiveRecord(contractId, request);
    }

    @PostMapping("/api/contracts/{contractId}/archives/{archiveRecordId}/borrows")
    ResponseEntity<Map<String, Object>> borrowArchive(@PathVariable String contractId,
                                                      @PathVariable String archiveRecordId,
                                                      @RequestBody Map<String, Object> request) {
        return service.borrowArchive(contractId, archiveRecordId, request);
    }

    @PostMapping("/api/contracts/{contractId}/archive-borrows/{borrowRecordId}/return")
    ResponseEntity<Map<String, Object>> returnArchiveBorrow(@PathVariable String contractId,
                                                            @PathVariable String borrowRecordId,
                                                            @RequestBody Map<String, Object> request) {
        return service.returnArchiveBorrow(contractId, borrowRecordId, request);
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
    private final Map<String, ContractChangeState> contractChanges = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> changeSummaries = new ConcurrentHashMap<>();
    private final Map<String, ContractTerminationState> contractTerminations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> terminationSummaries = new ConcurrentHashMap<>();
    private final Map<String, ArchiveRecordState> archiveRecords = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> archiveSummaries = new ConcurrentHashMap<>();
    private final Map<String, ArchiveBorrowState> archiveBorrows = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> lifecycleSummaries = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> contractClassificationChains = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> contractSemanticReferenceRefs = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> documentConsumerEvents = new ArrayList<>();
    private final Map<String, OcrJobState> ocrJobs = new ConcurrentHashMap<>();
    private final Map<String, String> ocrJobByIdempotency = new ConcurrentHashMap<>();
    private final Map<String, OcrResultState> ocrResults = new ConcurrentHashMap<>();
    private final Map<String, String> currentOcrResultByDocumentVersion = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> ocrAuditEvents = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> ocrRetryFacts = new ConcurrentHashMap<>();
    private final Map<String, SearchSourceEnvelopeState> searchSourceEnvelopes = new ConcurrentHashMap<>();
    private final Map<String, SearchDocumentState> searchDocuments = new ConcurrentHashMap<>();
    private final Map<String, SearchResultSetState> searchResultSets = new ConcurrentHashMap<>();
    private final Map<String, AiApplicationJobState> aiApplicationJobs = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> aiContextEnvelopes = new ConcurrentHashMap<>();
    private final Map<String, AiApplicationResultState> aiApplicationResults = new ConcurrentHashMap<>();
    private final Map<String, ProtectedResultSnapshotState> protectedResultSnapshots = new ConcurrentHashMap<>();
    private final Map<String, CandidateRankingSnapshotState> candidateRankingSnapshots = new ConcurrentHashMap<>();
    private final Map<String, QualityEvaluationReportState> qualityEvaluationReports = new ConcurrentHashMap<>();
    private final Map<String, CandidateWritebackGateState> candidateWritebackGates = new ConcurrentHashMap<>();
    private String activeCandidateProfileVersion = "v1";
    private int activeSearchGeneration = 1;
    private final List<Map<String, Object>> lifecycleTimelineEvents = new ArrayList<>();
    private final List<Map<String, Object>> lifecycleAuditEvents = new ArrayList<>();
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AgentOsGateway agentOsGateway;

    CoreChainService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AgentOsGateway agentOsGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.agentOsGateway = agentOsGateway;
    }

    Map<String, Object> createContract(Map<String, Object> request) {
        String contractId = "ctr-" + UUID.randomUUID();
        String contractNo = "CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ContractState contract = new ContractState(contractId, contractNo, text(request, "contract_name", "未命名合同"), "DRAFT",
                text(request, "owner_org_unit_id", null), text(request, "owner_user_id", null), text(request, "amount", null),
                text(request, "currency", null), null, new ArrayList<>(), null, null, new ArrayList<>());
        contract.events().add(event("CONTRACT_CREATED", contractId, text(request, "trace_id", null)));
        contracts.put(contractId, contract);
        contractClassificationChains.put(contractId, classificationChain(request));
        contractSemanticReferenceRefs.put(contractId, semanticReferenceRefs(request));
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
        supersedeOcrResultsForVersionSwitch(asset, versionId, text(request, "trace_id", null));
        supersedeCandidateSnapshotsForDocumentVersion(asset.currentVersionId(), text(request, "trace_id", null));
        documentAssets.put(documentAssetId, updated);
        refreshContractDocumentRef(updated, text(request, "trace_id", null));
        acceptEncryptionCheckIn(documentAssetId, versionId, "NEW_VERSION", request, false);
        appendDocumentConsumerEvent("DOCUMENT_VERSION_APPENDED", updated, versionId, text(request, "trace_id", null));
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
        if (!documentVersionId.equals(asset.currentVersionId())) {
            supersedeOcrResultsForVersionSwitch(asset, documentVersionId, text(request, "trace_id", null));
            supersedeCandidateSnapshotsForDocumentVersion(asset.currentVersionId(), text(request, "trace_id", null));
        }
        DocumentAssetState updated = new DocumentAssetState(asset.documentAssetId(), documentVersionId, asset.ownerType(), asset.ownerId(), asset.documentRole(), asset.documentTitle(),
                asset.documentStatus(), asset.encryptionStatus(), asset.previewStatus(), asset.latestVersionNo(), copyEvents(asset.auditRecords()));
        updated.auditRecords().add(event("DOCUMENT_VERSION_ACTIVATED", documentVersionId, text(request, "trace_id", null)));
        documentAssets.put(asset.documentAssetId(), updated);
        refreshContractDocumentRef(updated, text(request, "trace_id", null));
        appendDocumentConsumerEvent("DOCUMENT_VERSION_ACTIVATED", updated, documentVersionId, text(request, "trace_id", null));
        return documentAssetBody(updated);
    }

    Map<String, Object> batch4ContractConsumptionSummary(String contractId) {
        ContractState contract = requireContract(contractId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contract.contractId());
        body.put("contract_summary", contractBody(contract));
        body.put("classification_master_link", classificationMasterLink(contractId));
        body.put("semantic_reference_summary", contractSemanticReferences(contractId));
        body.put("consumption_contract_version", "batch4-startup-gate-v1");
        return body;
    }

    Map<String, Object> contractSemanticReferences(String contractId) {
        requireContract(contractId);
        Map<String, Object> refs = contractSemanticReferenceRefs.getOrDefault(contractId, semanticReferenceRefs(Map.of()));
        String clauseLibraryCode = text(refs, "clause_library_code", "clause-lib-default");
        String templateLibraryCode = text(refs, "template_library_code", "tpl-lib-default");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contractId);
        body.put("clause_library_ref", Map.of("library_code", clauseLibraryCode, "source_of_truth", "contract-core"));
        body.put("template_library_ref", Map.of("library_code", templateLibraryCode, "source_of_truth", "contract-core"));
        body.put("clause_library", Map.of("items", List.of(Map.of(
                "clause_id", "clause-" + clauseLibraryCode,
                "stable_clause_version_id", "clause-ver-" + clauseLibraryCode,
                "clause_title", "标准条款",
                "source_of_truth", "contract-core"))));
        body.put("template_library", Map.of("items", List.of(Map.of(
                "template_id", "tpl-" + templateLibraryCode,
                "stable_template_version_id", "tpl-ver-" + templateLibraryCode,
                "template_title", "标准模板",
                "source_of_truth", "contract-core"))));
        return body;
    }

    Map<String, Object> documentCapabilityBindings(String documentAssetId) {
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_asset_id", asset.documentAssetId());
        body.put("current_version_id", asset.currentVersionId());
        body.put("owner_type", asset.ownerType());
        body.put("owner_id", asset.ownerId());
        body.put("capability_bindings", capabilityBindings(asset));
        return body;
    }

    Map<String, Object> documentCenterEvents(String consumerScope) {
        String scope = consumerScope == null ? "BATCH4_INTELLIGENT_APPLICATIONS" : consumerScope;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subscription_entry", Map.of(
                "consumer_scope", scope,
                "subscribed_event_types", List.of("DOCUMENT_VERSION_APPENDED", "DOCUMENT_VERSION_ACTIVATED", "OCR_RESULT_READY", "OCR_RESULT_SUPERSEDED", "OCR_REBUILD_REQUESTED", "IA_SEARCH_REINDEX_REQUESTED"),
                "subscription_status", "ACTIVE"));
        body.put("items", documentConsumerEvents.stream().map(event -> (Map<String, Object>) event).toList());
        body.put("total", documentConsumerEvents.size());
        return body;
    }

    ResponseEntity<Map<String, Object>> createOcrJob(String permissions, String idempotencyHeader, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("CONTRACT_VIEW") || !permissions.contains("OCR_CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("OCR_INPUT_PERMISSION_DENIED", "缺少 OCR 创建或文档查看权限"));
        }
        OcrInput input = resolveOcrInput(request);
        String jobPurpose = text(request, "job_purpose", "TEXT_EXTRACTION");
        String idempotencyKey = text(request, "idempotency_key", idempotencyHeader == null ? "ocr-default-idem" : idempotencyHeader);
        String idempotencyFingerprint = input.documentVersion().documentVersionId() + "|" + jobPurpose + "|" + idempotencyKey;
        String existingJobId = ocrJobByIdempotency.get(idempotencyFingerprint);
        if (existingJobId != null) {
            Map<String, Object> replay = ocrJobBody(requireOcrJob(existingJobId), true);
            return ResponseEntity.ok(replay);
        }

        String jobId = "ocr-job-" + UUID.randomUUID();
        String traceId = text(request, "trace_id", "trace-" + UUID.randomUUID());
        String actorId = text(request, "actor_id", "system");
        String contentFingerprint = contentFingerprint(input.documentVersion());
        int maxAttempts = intValue(request, "max_attempt_no", 3);
        String platformJobId = createPlatformJob("IA_OCR_RECOGNITION", "intelligent-applications", "intelligent-applications",
                "OCR_JOB", jobId, "CONTRACT", input.asset().ownerId(), traceId, "ia-ocr-runner");
        OcrJobState accepted = new OcrJobState(jobId, input.asset().ownerId(), input.asset().documentAssetId(), input.documentVersion().documentVersionId(),
                contentFingerprint, jobPurpose, "ACCEPTED", json(Map.of("language_hints", listStrings(request.get("language_hints")))), "OCR_BASELINE",
                "OCR_BASELINE_TEXT", 0, maxAttempts, null, null, null, idempotencyKey, traceId, actorId, platformJobId, Instant.now().toString(), Instant.now().toString());
        ocrJobs.put(jobId, accepted);
        ocrJobByIdempotency.put(idempotencyFingerprint, jobId);
        persistOcrJob(accepted);
        appendOcrAudit(accepted, null, "OCR_JOB_CREATED", "ACCEPTED", null);

        OcrJobState completed = executeOcrJob(accepted, request);
        HttpStatus status = existingJobId == null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ocrJobBody(completed, false));
    }

    Map<String, Object> ocrJob(String ocrJobId) {
        return ocrJobBody(requireOcrJob(ocrJobId), false);
    }

    Map<String, Object> ocrResult(String resultId) {
        return ocrResultBody(requireOcrResult(resultId));
    }

    Map<String, Object> ocrResults(String documentAssetId, String documentVersionId, boolean defaultOnly) {
        List<Map<String, Object>> items = ocrResults.values().stream()
                .filter(result -> documentAssetId == null || documentAssetId.equals(result.documentAssetId()))
                .filter(result -> documentVersionId == null || documentVersionId.equals(result.documentVersionId()))
                .filter(result -> !defaultOnly || result.defaultConsumable())
                .map(this::ocrResultBody)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    ResponseEntity<Map<String, Object>> refreshSearchSources(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("SEARCH_INDEX_MANAGE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("SEARCH_INDEX_PERMISSION_DENIED", "缺少搜索索引管理权限"));
        }
        List<String> sourceTypes = listStrings(request.get("source_types"));
        if (sourceTypes.isEmpty()) {
            sourceTypes = List.of("CONTRACT", "DOCUMENT", "OCR", "CLAUSE");
        }
        List<SearchSourceEnvelopeState> envelopes = new ArrayList<>();
        for (String sourceType : sourceTypes) {
            SearchSourceEnvelopeState envelope = buildSearchSourceEnvelope(sourceType, request);
            envelopes.add(envelope);
            searchSourceEnvelopes.put(envelope.envelopeId(), envelope);
            persistSearchSourceEnvelope(envelope);
            if ("DOCUMENT".equals(envelope.docType())) {
                markOldDocumentVersionsStale(envelope.documentAssetId(), envelope.documentVersionId());
            }
            SearchDocumentState document = mapSearchDocument(envelope, activeSearchGeneration);
            searchDocuments.put(searchDocumentKey(document.searchDocId(), document.rebuildGeneration()), document);
            persistSearchDocument(document);
        }
        createPlatformJob("IA_SEARCH_REINDEX", "intelligent-applications", "intelligent-applications", "SEARCH_SOURCE",
                text(request, "contract_id", null), "CONTRACT", text(request, "contract_id", null), text(request, "trace_id", null), "ia-search-reindex-runner");
        appendSearchAudit("SEARCH_SOURCE_REFRESHED", text(request, "actor_id", "system"), text(request, "contract_id", null), null, "SUCCESS", text(request, "trace_id", null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refresh_status", "ACCEPTED");
        body.put("accepted_count", envelopes.size());
        body.put("source_envelopes", envelopes.stream().map(this::searchSourceEnvelopeBody).toList());
        body.put("search_documents", envelopes.stream()
                .map(envelope -> searchDocuments.get(searchDocumentKey(searchDocId(envelope.docType(), envelope.sourceObjectId(), envelope.sourceVersionDigest()), activeSearchGeneration)))
                .map(document -> searchDocumentBody(document, false))
                .toList());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    ResponseEntity<Map<String, Object>> search(String permissions, String orgScope, String deniedObjects, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("SEARCH_QUERY") || !permissions.contains("CONTRACT_VIEW")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("SEARCH_QUERY_PERMISSION_DENIED", "缺少搜索查询权限"));
        }
        boolean degraded = bool(request, "simulate_engine_unavailable", false);
        List<SearchDocumentState> allCandidates = searchCandidates(request, orgScope, deniedObjects, degraded);
        int page = intValue(request, "page", 1);
        int pageSize = intValue(request, "page_size", 10);
        List<SearchDocumentState> candidates = allCandidates.stream()
                .skip(Math.max(0, page - 1) * (long) pageSize)
                .limit(pageSize)
                .toList();
        String resultSetId = "search-rs-" + UUID.randomUUID();
        String actorId = text(request, "actor_id", "system");
        String permissionDigest = permissionDigest(permissions, orgScope);
        List<Map<String, Object>> views = candidates.stream().map(doc -> searchResultView(doc, permissions)).toList();
        String stableOrderDigest = "sha256:" + Integer.toHexString(views.stream().map(item -> text(item, "search_doc_id", "")).toList().toString().hashCode());
        Map<String, Object> facets = facets(allCandidates);
        SearchResultSetState resultSet = new SearchResultSetState(resultSetId, "READY", json(request), json(views), json(facets), allCandidates.size(),
                "DEFAULT_STABLE", Instant.now().plusSeconds(900).toString(), false, permissionDigest, actorId, activeSearchGeneration, stableOrderDigest, Instant.now().toString());
        searchResultSets.put(resultSetId, resultSet);
        persistSearchResultSet(resultSet);
        appendSearchAudit(degraded ? "SEARCH_DEGRADED_QUERY" : "SEARCH_QUERY_SNAPSHOT_CREATED", actorId, null, resultSetId, "SUCCESS", text(request, "trace_id", null));

        Map<String, Object> body = searchResultSetBody(resultSet, views, facets);
        body.put("degraded_query", degraded);
        if (degraded) {
            body.put("degrade_reason", "SEARCH_ENGINE_UNAVAILABLE");
        }
        return ResponseEntity.ok(body);
    }

    ResponseEntity<Map<String, Object>> searchSnapshot(String resultSetId, String permissions, String orgScope) {
        if (!hasSearchQueryPermission(permissions)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("SEARCH_QUERY_PERMISSION_DENIED", "缺少搜索查询权限"));
        }
        SearchResultSetState resultSet = requireSearchResultSet(resultSetId);
        if (!"READY".equals(resultSet.resultStatus())) {
            return ResponseEntity.status(HttpStatus.GONE).body(error("SEARCH_SNAPSHOT_EXPIRED", "搜索快照已失效"));
        }
        if (isSearchSnapshotExpired(resultSet)) {
            expireSearchResultSet(resultSet);
            return ResponseEntity.status(HttpStatus.GONE).body(error("SEARCH_SNAPSHOT_EXPIRED", "搜索快照已过期"));
        }
        if (!resultSet.permissionScopeDigest().equals(permissionDigest(permissions, orgScope))) {
            expireSearchResultSet(resultSet);
            return ResponseEntity.status(HttpStatus.GONE).body(error("SEARCH_SNAPSHOT_EXPIRED", "搜索快照权限语义已失效"));
        }
        Map<String, Object> body = searchResultSetBody(resultSet, listMapsFromJson(resultSet.itemPayloadJson()), mapFromJson(resultSet.facetPayloadJson()));
        body.put("snapshot_status", resultSet.resultStatus());
        return ResponseEntity.ok(body);
    }

    ResponseEntity<Map<String, Object>> exportSearchSnapshot(String resultSetId, String permissions, String orgScope, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("SEARCH_EXPORT")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("SEARCH_EXPORT_PERMISSION_DENIED", "缺少搜索导出权限"));
        }
        ResponseEntity<Map<String, Object>> snapshot = searchSnapshot(resultSetId, permissions, orgScope);
        if (!snapshot.getStatusCode().is2xxSuccessful()) {
            return snapshot;
        }
        String exportId = "search-export-" + UUID.randomUUID();
        String artifactRef = "search-export://" + exportId;
        jdbcTemplate.update("""
                insert into ia_search_export_record
                (export_id, result_set_id, export_profile_code, export_status, artifact_ref, item_count, permission_scope_digest, trace_id, created_at)
                values (?, ?, ?, 'READY', ?, ?, ?, ?, ?)
                """, exportId, resultSetId, text(request, "export_profile_code", "DEFAULT"), artifactRef,
                intValue(snapshot.getBody(), "total", 0), permissionDigest(permissions, orgScope), text(request, "trace_id", null), Timestamp.from(Instant.now()));
        appendSearchAudit("SEARCH_EXPORT_CREATED", text(request, "actor_id", "system"), null, resultSetId, "SUCCESS", text(request, "trace_id", null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("export_id", exportId);
        body.put("result_set_id", resultSetId);
        body.put("export_status", "READY");
        body.put("artifact_ref", artifactRef);
        body.put("items", exportItems(listMaps(snapshot.getBody().get("items"))));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    ResponseEntity<Map<String, Object>> replaySearchSnapshot(String resultSetId, String permissions, String orgScope, Map<String, Object> request) {
        ResponseEntity<Map<String, Object>> snapshot = searchSnapshot(resultSetId, permissions, orgScope);
        if (!snapshot.getStatusCode().is2xxSuccessful()) {
            return snapshot;
        }
        Map<String, Object> body = new LinkedHashMap<>(snapshot.getBody());
        body.put("replay_source", "SNAPSHOT");
        appendSearchAudit("SEARCH_SNAPSHOT_REPLAYED", text(request, "actor_id", "system"), null, resultSetId, "SUCCESS", text(request, "trace_id", null));
        return ResponseEntity.ok(body);
    }

    ResponseEntity<Map<String, Object>> rebuildSearchIndex(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("SEARCH_INDEX_MANAGE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("SEARCH_INDEX_PERMISSION_DENIED", "缺少搜索索引管理权限"));
        }
        int oldGeneration = activeSearchGeneration;
        int nextGeneration = oldGeneration + 1;
        boolean switchAlias = bool(request, "switch_alias", true);
        String scopedContractId = text(map(request.get("scope")), "contract_id", null);
        int backfilled = 0;
        for (SearchDocumentState document : new ArrayList<>(searchDocuments.values())) {
            if (document.rebuildGeneration() == oldGeneration && "ACTIVE".equals(document.exposureStatus()) && (scopedContractId == null || scopedContractId.equals(document.contractId()))) {
                SearchDocumentState rebuilt = new SearchDocumentState(document.searchDocId(), document.docType(), document.sourceObjectId(), document.sourceAnchorJson(),
                        document.sourceVersionDigest(), document.contractId(), document.documentAssetId(), document.documentVersionId(), document.semanticRefId(), document.titleText(),
                        document.bodyText(), document.keywordText(), document.filterPayloadJson(), document.sortPayloadJson(), document.localeCode(), document.visibilityScopeJson(),
                        "ACTIVE", nextGeneration, Instant.now().toString());
                searchDocuments.put(searchDocumentKey(rebuilt.searchDocId(), rebuilt.rebuildGeneration()), rebuilt);
                persistSearchDocument(rebuilt);
                backfilled++;
            }
        }
        if (switchAlias) {
            activeSearchGeneration = nextGeneration;
        }
        String rebuildStatus = switchAlias ? "SWITCHED" : "BUILT";
        String aliasStatus = switchAlias ? "ACTIVE" : "STAGED";
        String rebuildId = "search-rebuild-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into ia_search_rebuild_job
                (rebuild_job_id, rebuild_type, rebuild_status, scope_json, old_generation, new_generation, backfilled_count, alias_status, trace_id, created_at, completed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rebuildId, text(request, "rebuild_type", "FULL"), rebuildStatus, json(request.get("scope")), oldGeneration, nextGeneration,
                backfilled, aliasStatus, text(request, "trace_id", null), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        appendSearchAudit(switchAlias ? "SEARCH_REBUILD_SWITCHED" : "SEARCH_REBUILD_BUILT", text(request, "actor_id", "system"), null, null, "SUCCESS", text(request, "trace_id", null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rebuild_job_id", rebuildId);
        body.put("rebuild_type", text(request, "rebuild_type", "FULL"));
        body.put("rebuild_status", rebuildStatus);
        body.put("old_generation", oldGeneration);
        body.put("new_generation", nextGeneration);
        body.put("backfill_result", Map.of("backfilled_count", backfilled));
        body.put("alias_switch", Map.of("alias_status", aliasStatus, "active_generation", activeSearchGeneration, "candidate_generation", nextGeneration));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    ResponseEntity<Map<String, Object>> expireSearchSnapshotsForPermissionChange(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("SEARCH_INDEX_MANAGE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("SEARCH_INDEX_PERMISSION_DENIED", "缺少搜索索引管理权限"));
        }
        String actorId = text(request, "actor_id", null);
        int expired = 0;
        for (SearchResultSetState resultSet : new ArrayList<>(searchResultSets.values())) {
            if (actorId == null || actorId.equals(resultSet.actorId())) {
                expireSearchResultSet(resultSet);
                expired++;
            }
        }
        appendSearchAudit("SEARCH_PERMISSION_SNAPSHOT_EXPIRED", actorId, null, null, "SUCCESS", text(request, "trace_id", null));
        return ResponseEntity.ok(Map.of("expired_snapshot_count", expired));
    }

    ResponseEntity<Map<String, Object>> createAiApplicationJob(String permissions, String orgScope, String idempotencyHeader, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("AI_APPLICATION_CREATE") || !permissions.contains("CONTRACT_VIEW") || !permissions.contains("SEARCH_QUERY")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("AI_APPLICATION_PERMISSION_DENIED", "缺少智能应用创建、合同查看或搜索查询权限"));
        }
        String applicationType = text(request, "application_type", "SUMMARY");
        if (!List.of("SUMMARY", "QA", "RISK_ANALYSIS", "DIFF_EXTRACTION").contains(applicationType)) {
            return ResponseEntity.unprocessableEntity().body(error("AI_APPLICATION_TYPE_INVALID", "智能应用类型不在允许范围内"));
        }
        ContractState contract = requireContract(text(request, "contract_id", null));
        if (orgScope != null && !orgScope.isBlank() && contract.ownerOrgUnitId() != null && !orgScope.equals(contract.ownerOrgUnitId())) {
            Map<String, Object> protectedSnapshot = createStandaloneProtectedSnapshot(applicationType, contract.contractId(), "REJECT", "AI_APPLICATION_SCOPE_DENIED", request);
            Map<String, Object> body = error("AI_APPLICATION_SCOPE_DENIED", "问题超出调用方可见范围");
            body.put("protected_result_snapshot", protectedSnapshot);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        List<SearchDocumentState> evidenceDocs = aiEvidenceDocuments(contract.contractId(), text(request, "document_version_id", null), orgScope);
        if (evidenceDocs.isEmpty()) {
            Map<String, Object> protectedSnapshot = createStandaloneProtectedSnapshot(applicationType, contract.contractId(), "REJECT", "AI_CONTEXT_NO_EVIDENCE", request);
            Map<String, Object> body = error("AI_CONTEXT_NO_EVIDENCE", "缺少可绑定来源证据，拒绝执行智能任务");
            body.put("protected_result_snapshot", protectedSnapshot);
            return ResponseEntity.unprocessableEntity().body(body);
        }

        String idempotencyKey = text(request, "idempotency_key", idempotencyHeader == null ? "ai-default-idem" : idempotencyHeader);
        String scopeDigest = "sha256:" + Integer.toHexString((contract.contractId() + ":" + text(request, "document_version_id", "") + ":" + applicationType).hashCode());
        AiApplicationJobState existingJob = findAiJobByIdempotency(applicationType, contract.contractId(), idempotencyKey);
        if (existingJob != null) {
            if (!scopeDigest.equals(existingJob.scopeDigest())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error("AI_APPLICATION_IDEMPOTENCY_CONFLICT", "同一幂等键对应的智能应用范围不一致"));
            }
            Map<String, Object> body = aiJobResponse(existingJob, aiContextEnvelopes.get(existingJob.resultContextId()), requireAiResultByJob(existingJob.aiApplicationJobId()),
                    protectedSnapshotByJob(existingJob.aiApplicationJobId()), null, new AgentOsBinding(existingJob.agentTaskId(), null, existingJob.agentResultId(), existingJob.failureCode()));
            body.put("duplicate", true);
            return ResponseEntity.ok(body);
        }
        String jobId = "ai-job-" + UUID.randomUUID();
        String contextAssemblyJobId = "ai-ctx-job-" + UUID.randomUUID();
        String resultContextId = "ai-ctx-" + UUID.randomUUID();
        String traceId = text(request, "trace_id", "trace-" + UUID.randomUUID());
        String actorId = text(request, "actor_id", "system");
        List<Map<String, Object>> evidenceSegments = buildEvidenceSegments(evidenceDocs, request);
        if (!canFitEvidenceBudget(evidenceSegments, request)) {
            Map<String, Object> protectedSnapshot = createStandaloneProtectedSnapshot(applicationType, contract.contractId(), "REJECT", "AI_CONTEXT_BUDGET_INSUFFICIENT", request);
            Map<String, Object> body = error("AI_CONTEXT_BUDGET_INSUFFICIENT", "证据预算不足，无法保留必要事实和引用，拒绝执行智能任务");
            body.put("protected_result_snapshot", protectedSnapshot);
            body.put("budget_decision", Map.of("degradation_result", "REJECTED_BEFORE_AGENT_OS", "available_scope", "请缩小合同或文档范围后重试"));
            return ResponseEntity.unprocessableEntity().body(body);
        }
        Map<String, Object> envelope = buildAiContextEnvelope(resultContextId, jobId, contextAssemblyJobId, applicationType, contract, evidenceDocs, evidenceSegments, request);
        persistAiContextEnvelope(resultContextId, jobId, contextAssemblyJobId, applicationType, contract.contractId(), envelope);

        AgentOsBinding agent = createAgentOsAiTask(jobId, applicationType, contract.contractId(), resultContextId, actorId, idempotencyKey, traceId, bool(request, "simulate_agent_timeout", false));
        if (bool(request, "simulate_agent_timeout", false)) {
            AiApplicationResultState failed = createAiResult(jobId, applicationType, agent.taskId(), agent.resultId(), "FAILED", "BLOCK", "AGENT_OS_TIMEOUT", false, false,
                    Map.of("failure_code", "AGENT_OS_TIMEOUT"), List.of(), List.of("AGENT_OS_TIMEOUT"));
            ProtectedResultSnapshotState snapshot = createProtectedSnapshot(jobId, failed.resultId(), agent.taskId(), agent.resultId(), "BLOCK", "AGENT_OS_TIMEOUT", false, Map.of("result_status", "FAILED"));
            AiApplicationJobState job = persistAiJob(jobId, applicationType, contract.contractId(), text(request, "document_version_id", null), "FAILED", contextAssemblyJobId,
                    resultContextId, agent.taskId(), agent.resultId(), idempotencyKey, scopeDigest, "AGENT_OS_TIMEOUT", "Agent OS 超时", traceId);
            appendAiAudit(jobId, "AI_AGENT_OS_TIMEOUT", actorId, "FAILED", traceId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(aiJobResponse(job, envelope, failed, snapshot, null, agent));
        }

        GuardrailOutcome guardrail = evaluateAiGuardrail(applicationType, evidenceSegments, request);
        boolean needsHuman = "REVIEW_REQUIRED".equals(guardrail.decision());
        String resultStatus = switch (guardrail.decision()) {
            case "PASS" -> "READY";
            case "PASS_PARTIAL" -> "PARTIAL";
            case "REJECT" -> "REJECTED";
            case "REVIEW_REQUIRED" -> "PARTIAL";
            default -> "FAILED";
        };
        AiApplicationResultState result = createAiResult(jobId, applicationType, agent.taskId(), agent.resultId(), resultStatus, guardrail.decision(), guardrail.failureCode(),
                needsHuman, "PASS".equals(guardrail.decision()), guardrail.payload(), guardrail.citations(), guardrail.riskFlags());
        ProtectedResultSnapshotState snapshot = null;
        Map<String, Object> confirmation = null;
        String jobStatus = resultStatus;
        if (!"PASS".equals(guardrail.decision())) {
            snapshot = createProtectedSnapshot(jobId, result.resultId(), agent.taskId(), agent.resultId(), guardrail.decision(), guardrail.failureCode(), needsHuman, guardrail.payload());
            jobStatus = needsHuman ? "WAITING_HUMAN_CONFIRMATION" : ("REJECT".equals(guardrail.decision()) ? "REJECTED" : "GUARDRAIL_BLOCKED");
        }
        if (needsHuman) {
            confirmation = createAiHumanConfirmation(agent.taskId(), agent.resultId(), contract.contractId(), applicationType, guardrail.payload(), evidenceSegments, actorId, traceId);
        }
        AiApplicationJobState job = persistAiJob(jobId, applicationType, contract.contractId(), text(request, "document_version_id", null), jobStatus, contextAssemblyJobId,
                resultContextId, agent.taskId(), agent.resultId(), idempotencyKey, scopeDigest, guardrail.failureCode(), null, traceId);
        appendAiAudit(jobId, "AI_GUARDRAIL_" + guardrail.decision(), actorId, jobStatus, traceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(aiJobResponse(job, envelope, result, snapshot, confirmation, agent));
    }

    ResponseEntity<Map<String, Object>> replayAiGuardrail(String snapshotId, String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("AI_GUARDRAIL_REPLAY")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("AI_GUARDRAIL_REPLAY_PERMISSION_DENIED", "缺少护栏重放权限"));
        }
        ProtectedResultSnapshotState snapshot = protectedResultSnapshots.get(snapshotId);
        if (snapshot == null) {
            throw new IllegalArgumentException("protected_result_snapshot_id 不存在: " + snapshotId);
        }
        appendAiAudit(snapshot.aiApplicationJobId(), "AI_GUARDRAIL_REPLAYED", text(request, "actor_id", "system"), snapshot.guardrailDecision(), text(request, "trace_id", null));
        return ResponseEntity.ok(Map.of(
                "replay_result", snapshot.guardrailDecision(),
                "protected_result_snapshot", protectedSnapshotBody(snapshot)));
    }

    ResponseEntity<Map<String, Object>> createCandidateRankingJob(String permissions, String orgScope, String idempotencyHeader, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("AI_APPLICATION_CREATE") || !permissions.contains("CONTRACT_VIEW") || !permissions.contains("SEARCH_QUERY")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("CANDIDATE_RANKING_PERMISSION_DENIED", "缺少候选排序、合同查看或搜索查询权限"));
        }
        String applicationType = text(request, "application_type", "SUMMARY");
        if (!List.of("SUMMARY", "QA", "RISK_ANALYSIS", "DIFF_EXTRACTION").contains(applicationType)) {
            return ResponseEntity.unprocessableEntity().body(error("CANDIDATE_APPLICATION_TYPE_INVALID", "候选排序任务类型不在允许范围内"));
        }
        ContractState contract = requireContract(text(request, "contract_id", null));
        if (orgScope != null && !orgScope.isBlank() && contract.ownerOrgUnitId() != null && !orgScope.equals(contract.ownerOrgUnitId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("CANDIDATE_SCOPE_DENIED", "候选来源超出调用方可见范围"));
        }
        String documentVersionId = text(request, "document_version_id", null);
        List<SearchDocumentState> evidenceDocs = aiEvidenceDocuments(contract.contractId(), documentVersionId, orgScope);
        if (evidenceDocs.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(error("CANDIDATE_SOURCE_EMPTY", "缺少可归一候选来源"));
        }

        String jobId = "ai-rank-job-" + UUID.randomUUID();
        String traceId = text(request, "trace_id", "trace-" + UUID.randomUUID());
        String actorId = text(request, "actor_id", "system");
        String rankingProfileCode = text(request, "ranking_profile_code", "CANDIDATE_RANKING_BASELINE");
        String qualityProfileCode = text(request, "quality_profile_code", "CANDIDATE_QUALITY_BASELINE");
        String profileVersion = activeCandidateProfileVersion;
        String sourceDigest = candidateSourceDigest(evidenceDocs, documentVersionId, applicationType);
        List<Map<String, Object>> evidenceSegments = buildEvidenceSegments(evidenceDocs, request);
        List<Map<String, Object>> semanticCandidates = buildSemanticCandidates(applicationType, contract, documentVersionId, evidenceDocs, evidenceSegments, sourceDigest, request);
        List<Map<String, Object>> activeCandidates = semanticCandidates.stream()
                .filter(candidate -> "ACTIVE".equals(candidate.get("elimination_status")) || "CONFLICTED".equals(candidate.get("elimination_status")))
                .toList();

        String rankingSnapshotId = "rank-snapshot-" + UUID.randomUUID();
        CandidateRankingSnapshotState rankingSnapshot = new CandidateRankingSnapshotState(rankingSnapshotId, jobId, applicationType, contract.contractId(), documentVersionId,
                sourceDigest, rankingProfileCode, profileVersion, qualityProfileCode, profileVersion, "READY", json(activeCandidates), json(semanticCandidates), Instant.now().plusSeconds(900).toString(), traceId, Instant.now().toString());
        candidateRankingSnapshots.put(rankingSnapshotId, rankingSnapshot);

        QualityDecision decision = evaluateCandidateQuality(applicationType, semanticCandidates, request);
        String qualityEvaluationId = "quality-eval-" + UUID.randomUUID();
        QualityEvaluationReportState quality = new QualityEvaluationReportState(qualityEvaluationId, rankingSnapshotId, applicationType, qualityProfileCode, profileVersion,
                decision.qualityTier(), decision.releaseDecision(), decision.reasonCodes(), decision.coverage(), decision.citation(), decision.consistency(), decision.completeness(), decision.publishability(), Instant.now().toString());
        qualityEvaluationReports.put(qualityEvaluationId, quality);

        Map<String, Object> guardrailMapping = guardrailMapping(decision.releaseDecision());
        boolean writebackAllowed = List.of("PUBLISH", "PARTIAL_PUBLISH").contains(decision.releaseDecision());
        String resultStatus = text(guardrailMapping, "result_status", "READY");
        AgentOsBinding agent = createAgentOsAiTask(jobId, applicationType, contract.contractId(), rankingSnapshotId, actorId,
                text(request, "idempotency_key", idempotencyHeader == null ? "rank-default-idem" : idempotencyHeader), traceId, false);
        Map<String, Object> rankingBody = candidateRankingSnapshotBody(rankingSnapshot, semanticCandidates);
        Map<String, Object> qualityBody = qualityEvaluationBody(quality);
        Map<String, Object> gatePayload = candidateWritebackGateBody(new CandidateWritebackGateState("PENDING_RESULT", rankingSnapshotId, qualityEvaluationId, decision.releaseDecision(),
                writebackAllowed ? "READY" : "BLOCKED", writebackAllowed, traceId, Instant.now().toString()));
        Map<String, Object> structuredPayload = new LinkedHashMap<>();
        structuredPayload.put("ranking_snapshot_id", rankingSnapshotId);
        structuredPayload.put("quality_evaluation_id", qualityEvaluationId);
        structuredPayload.put("release_decision", decision.releaseDecision());
        structuredPayload.put("ranking_snapshot", rankingBody);
        structuredPayload.put("semantic_candidates", semanticCandidates);
        structuredPayload.put("quality_evaluation", qualityBody);
        structuredPayload.put("writeback_gate", gatePayload);
        AiApplicationResultState result = createAiResult(jobId, applicationType, agent.taskId(), agent.resultId(), resultStatus,
                text(guardrailMapping, "guardrail_decision", "PASS"), text(guardrailMapping, "guardrail_failure_code", null), !writebackAllowed, writebackAllowed,
                structuredPayload,
                activeCandidates.stream().limit(2).map(candidate -> Map.of("candidate_id", candidate.get("candidate_id"), "citation_ref", text(candidate, "source_anchor_summary", "candidate"))).toList(),
                decision.reasonCodes());
        gatePayload.put("result_id", result.resultId());
        updateAiResultStructuredPayload(result.resultId(), structuredPayload);
        ProtectedResultSnapshotState protectedSnapshot = null;
        Map<String, Object> confirmation = null;
        if (!writebackAllowed) {
            protectedSnapshot = createProtectedSnapshot(jobId, result.resultId(), agent.taskId(), agent.resultId(), text(guardrailMapping, "guardrail_decision", "REJECT"),
                    text(guardrailMapping, "guardrail_failure_code", decision.releaseDecision()), "ESCALATE_TO_HUMAN".equals(decision.releaseDecision()),
                    Map.of("ranking_snapshot_id", rankingSnapshotId, "quality_evaluation_id", qualityEvaluationId, "decision_reason_code_list", decision.reasonCodes()));
        }
        if ("ESCALATE_TO_HUMAN".equals(decision.releaseDecision())) {
            confirmation = createAiHumanConfirmation(agent.taskId(), agent.resultId(), contract.contractId(), applicationType,
                    Map.of("release_decision", decision.releaseDecision(), "quality_tier", decision.qualityTier()), activeCandidates, actorId, traceId);
        }
        String jobStatus = text(guardrailMapping, "job_status", "SUCCEEDED");
        persistAiJob(jobId, applicationType, contract.contractId(), documentVersionId, jobStatus, "candidate-ranking", rankingSnapshotId, agent.taskId(), agent.resultId(),
                text(request, "idempotency_key", idempotencyHeader == null ? "rank-default-idem" : idempotencyHeader), sourceDigest, text(guardrailMapping, "guardrail_failure_code", null), null, traceId);

        CandidateWritebackGateState gate = new CandidateWritebackGateState(result.resultId(), rankingSnapshotId, qualityEvaluationId, decision.releaseDecision(),
                writebackAllowed ? "READY" : "BLOCKED", writebackAllowed, traceId, Instant.now().toString());
        candidateWritebackGates.put(result.resultId(), gate);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ai_application_job_id", jobId);
        body.put("result_id", result.resultId());
        body.put("application_type", applicationType);
        body.put("job_status", jobStatus);
        body.put("ranking_snapshot", rankingBody);
        body.put("quality_evaluation", qualityBody);
        body.put("guardrail_mapping", guardrailMapping);
        body.put("guarded_result", aiResultBody(result));
        body.put("writeback_gate", candidateWritebackGateBody(gate));
        body.put("agent_os", Map.of("agent_task_id", agent.taskId(), "agent_result_id", agent.resultId()));
        if (protectedSnapshot != null) {
            body.put("protected_result_snapshot", protectedSnapshotBody(protectedSnapshot));
        }
        if (confirmation != null) {
            body.put("human_confirmation", confirmation);
        }
        appendCandidateAudit("CANDIDATE_RANKING_COMPLETED", jobId, rankingSnapshotId, qualityEvaluationId, decision.releaseDecision(), actorId, traceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    ResponseEntity<Map<String, Object>> switchCandidateProfiles(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("AI_APPLICATION_CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("CANDIDATE_PROFILE_PERMISSION_DENIED", "缺少候选档位切换权限"));
        }
        String newVersion = text(request, "new_profile_version", "v2");
        activeCandidateProfileVersion = newVersion;
        for (CandidateRankingSnapshotState snapshot : new ArrayList<>(candidateRankingSnapshots.values())) {
            if ("READY".equals(snapshot.snapshotStatus())) {
                CandidateRankingSnapshotState superseded = snapshot.withStatus("SUPERSEDED");
                candidateRankingSnapshots.put(snapshot.rankingSnapshotId(), superseded);
                updateCandidateSnapshotStatusInResult(snapshot.rankingSnapshotId(), "SUPERSEDED");
            }
        }
        return ResponseEntity.ok(Map.of("profile_switch_status", "SWITCHED", "new_profile_version", newVersion));
    }

    ResponseEntity<Map<String, Object>> replayCandidateRankingSnapshot(String snapshotId, String permissions, String orgScope, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("AI_APPLICATION_CREATE") || !permissions.contains("CONTRACT_VIEW") || !permissions.contains("SEARCH_QUERY")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("CANDIDATE_REPLAY_PERMISSION_DENIED", "缺少候选快照重放权限"));
        }
        CandidateRankingSnapshotState snapshot = loadCandidateSnapshot(snapshotId);
        if (snapshot == null) {
            throw new IllegalArgumentException("ranking_snapshot_id 不存在: " + snapshotId);
        }
        if (!"READY".equals(snapshot.snapshotStatus()) || !snapshot.rankingProfileVersion().equals(activeCandidateProfileVersion) || candidateDocumentVersionChanged(snapshot)) {
            CandidateRankingSnapshotState superseded = snapshot.withStatus("SUPERSEDED");
            candidateRankingSnapshots.put(snapshotId, superseded);
            updateCandidateSnapshotStatusInResult(snapshotId, "SUPERSEDED");
            return ResponseEntity.status(HttpStatus.GONE).body(error("CANDIDATE_SNAPSHOT_SUPERSEDED", "候选快照已因版本切换失效"));
        }
        return ResponseEntity.ok(candidateRankingSnapshotBody(snapshot, listMapsFromJson(snapshot.candidateListJson())));
    }

    ResponseEntity<Map<String, Object>> createCandidateWriteback(String permissions, Map<String, Object> request) {
        if (permissions == null || !permissions.contains("AI_APPLICATION_CREATE") || !permissions.contains("CONTRACT_VIEW")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("CANDIDATE_WRITEBACK_PERMISSION_DENIED", "缺少候选回写候选创建权限"));
        }
        String resultId = text(request, "result_id", null);
        CandidateWritebackGateState gate = loadCandidateWritebackGate(resultId);
        if (gate == null) {
            return ResponseEntity.unprocessableEntity().body(error("CANDIDATE_QUALITY_ANCHOR_REQUIRED", "回写候选缺少 ranking_snapshot_id 或 quality_evaluation_id"));
        }
        if (!gate.writebackAllowed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CANDIDATE_RELEASE_DECISION_BLOCKS_WRITEBACK", "放行决策未允许进入回写候选队列"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(candidateWritebackGateBody(gate));
    }

    private List<Map<String, Object>> buildSemanticCandidates(String applicationType, ContractState contract, String documentVersionId,
                                                              List<SearchDocumentState> evidenceDocs, List<Map<String, Object>> evidenceSegments,
                                                              String sourceDigest, Map<String, Object> request) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        int order = 1;
        for (SearchDocumentState document : evidenceDocs) {
            String slot = switch (document.docType()) {
                case "CLAUSE" -> "RISK_BASELINE";
                case "OCR" -> "SUMMARY_FACT";
                case "DOCUMENT" -> "DIFF_BASELINE";
                default -> primarySlot(applicationType);
            };
            candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "SEARCH_HIT", applicationType, contract.contractId(), document.documentVersionId(),
                    document.sourceObjectId(), document.sourceAnchorJson(), slot, document.titleText(), 0.88 - order * 0.01, "ACTIVE", sourceDigest, order++));
        }
        OcrResultState ocr = currentOcrResultByDocumentVersion.containsKey(documentVersionId) ? ocrResults.get(currentOcrResultByDocumentVersion.get(documentVersionId)) : null;
        if (ocr != null && !ocr.fieldCandidates().isEmpty()) {
            Map<String, Object> field = ocr.fieldCandidates().get(0);
            candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "OCR_FIELD", applicationType, contract.contractId(), documentVersionId,
                    text(field, "field_candidate_id", null), json(Map.of("document_version_id", documentVersionId, "page_no", field.get("page_no"))), "FIELD_VALUE",
                    text(field, "candidate_value", ""), 0.91, "ACTIVE", sourceDigest, order++));
            candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "OCR_FIELD", applicationType, contract.contractId(), documentVersionId,
                    text(field, "field_candidate_id", null), json(Map.of("document_version_id", documentVersionId, "page_no", field.get("page_no"))), "FIELD_VALUE",
                    text(field, "candidate_value", ""), 0.72, "RESERVED", sourceDigest, order++));
        }
        Map<String, Object> refs = contractSemanticReferences(contract.contractId());
        String clauseVersion = text(listMaps(map(refs.get("clause_library")).get("items")).get(0), "stable_clause_version_id", "clause-ver-default");
        String templateVersion = text(listMaps(map(refs.get("template_library")).get("items")).get(0), "stable_template_version_id", "tpl-ver-default");
        candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "CLAUSE_REF", applicationType, contract.contractId(), null,
                clauseVersion, json(Map.of("clause_version_id", clauseVersion)), "RISK_BASELINE", "标准条款基线", 0.94, "ACTIVE", sourceDigest, order++));
        candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "TEMPLATE_REF", applicationType, contract.contractId(), null,
                templateVersion, json(Map.of("template_version_id", templateVersion)), "DIFF_BASELINE", "标准模板基线", 0.9, "ACTIVE", sourceDigest, order++));
        Map<String, Object> primaryEvidence = evidenceSegments.isEmpty() ? Map.of("evidence_segment_id", "evidence-" + contract.contractId(), "citation_ref", "candidate-evidence") : evidenceSegments.get(0);
        candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "EVIDENCE_SEGMENT", applicationType, contract.contractId(), documentVersionId,
                text(primaryEvidence, "evidence_segment_id", "evidence-" + contract.contractId()), json(primaryEvidence), evidenceSlot(applicationType),
                text(primaryEvidence, "segment_text", contract.contractName() + " 智能证据片段"), 0.89, "ACTIVE", sourceDigest, order++));
        Set<String> coveredSlots = new HashSet<>();
        for (Map<String, Object> candidate : candidates) {
            coveredSlots.add(text(candidate, "semantic_slot", "SUMMARY_FACT"));
        }
        for (String slot : List.of("ANSWER_SUPPORT", "RISK_EVIDENCE", "DIFF_DELTA")) {
            if (!coveredSlots.contains(slot)) {
                candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "EVIDENCE_SEGMENT", applicationType, contract.contractId(), documentVersionId,
                        text(primaryEvidence, "evidence_segment_id", "evidence-" + contract.contractId()), json(primaryEvidence), slot,
                        text(primaryEvidence, "segment_text", contract.contractName() + " 槽位补充证据"), 0.80 - order * 0.005, "ACTIVE", sourceDigest, order++));
            }
        }
        List<Map<String, Object>> ruleHits = listMaps(request.get("rule_hit_list"));
        if (ruleHits.isEmpty()) {
            ruleHits = List.of(Map.of("rule_version", "rule-version-" + applicationType + "-baseline", "rule_code", "BASELINE", "hit_status", "ACTIVE", "severity", "LOW", "strong_veto", false));
        }
        for (Map<String, Object> ruleHit : ruleHits) {
            String ruleVersion = text(ruleHit, "rule_version", "rule-version-" + applicationType + "-baseline");
            String status = bool(ruleHit, "strong_veto", false) ? "CONFLICTED" : "ACTIVE";
            candidates.add(semanticCandidate("cand-" + UUID.randomUUID(), "RULE_HIT", applicationType, contract.contractId(), documentVersionId,
                    ruleVersion, json(ruleHit), "RULE_OVERRIDE",
                    "规则命中 " + text(ruleHit, "rule_code", "BASELINE"), 0.97, status, sourceDigest, order++));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(candidate -> -doubleValue(candidate, "ranking_score", 0.0)))
                .toList();
    }

    private Map<String, Object> semanticCandidate(String id, String type, String applicationType, String contractId, String documentVersionId,
                                                  String sourceObjectId, String anchorJson, String slot, String payloadText, double score,
                                                  String status, String sourceDigest, int order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidate_id", id);
        body.put("candidate_type", type);
        body.put("application_type", applicationType);
        body.put("contract_id", contractId);
        body.put("document_version_id", documentVersionId);
        body.put("source_language", "zh-CN");
        body.put("normalized_language", "zh-CN");
        body.put("response_language", "zh-CN");
        body.put("source_object_id", sourceObjectId);
        body.put("source_anchor", mapFromJson(anchorJson));
        body.put("semantic_slot", slot);
        body.put("candidate_payload", Map.of("summary", payloadText));
        body.put("source_score", score);
        body.put("normalized_score", Map.of("source_reliability_score", score, "evidence_integrity_score", Math.min(1.0, score + 0.02), "risk_penalty_score", "RULE_HIT".equals(type) ? 0.0 : 0.03));
        body.put("ranking_score", score - ("RESERVED".equals(status) ? 0.2 : 0.0));
        body.put("elimination_status", status);
        body.put("explanation_digest", "来源=" + type + ", 槽位=" + slot + ", 排序=" + order);
        body.put("source_anchor_summary", type + ":" + sourceObjectId);
        body.put("candidate_digest", "sha256:" + Integer.toHexString((sourceDigest + type + sourceObjectId + slot + payloadText).hashCode()));
        return body;
    }

    private QualityDecision evaluateCandidateQuality(String applicationType, List<Map<String, Object>> candidates, Map<String, Object> request) {
        Map<String, Object> qualityInputs = map(request.get("quality_inputs"));
        boolean lowQuality = !qualityInputs.isEmpty() && (!bool(qualityInputs, "anchor_complete", true)
                || doubleValue(qualityInputs, "evidence_coverage_ratio", 1.0) < 0.50
                || doubleValue(qualityInputs, "citation_validity_score", 1.0) < 0.50);
        boolean unresolvedConflict = candidates.stream().anyMatch(candidate -> "CONFLICTED".equals(candidate.get("elimination_status")));
        if (lowQuality) {
            return new QualityDecision("TIER_D", "REJECT", List.of("LOW_QUALITY_REJECTED", "ANCHOR_OR_CITATION_INSUFFICIENT"), 0.20, 0.20, 0.15, 0.20, 0.10);
        }
        if (unresolvedConflict || "RISK_ANALYSIS".equals(applicationType)) {
            return new QualityDecision("TIER_C", "ESCALATE_TO_HUMAN", List.of("UNRESOLVED_CONFLICT", "HUMAN_REVIEW_REQUIRED"), 0.72, 0.82, 0.45, 0.70, 0.50);
        }
        if ("QA".equals(applicationType) || "DIFF_EXTRACTION".equals(applicationType)) {
            return new QualityDecision("TIER_B", "PARTIAL_PUBLISH", List.of("LOCAL_GAP_EXPLAINED", "PARTIAL_RELEASE_ALLOWED"), 0.78, 0.85, 0.76, 0.70, 0.74);
        }
        return new QualityDecision("TIER_A", "PUBLISH", List.of("EVIDENCE_SUFFICIENT", "CONFLICT_RESOLVED"), 0.95, 0.96, 0.93, 0.94, 0.95);
    }

    private Map<String, Object> guardrailMapping(String releaseDecision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("release_decision", releaseDecision);
        switch (releaseDecision) {
            case "PUBLISH" -> {
                body.put("guardrail_decision", "PASS");
                body.put("result_status", "READY");
                body.put("job_status", "SUCCEEDED");
            }
            case "PARTIAL_PUBLISH" -> {
                body.put("guardrail_decision", "PASS_PARTIAL");
                body.put("result_status", "PARTIAL");
                body.put("job_status", "SUCCEEDED");
                body.put("gap_statement_required", true);
            }
            case "ESCALATE_TO_HUMAN" -> {
                body.put("guardrail_decision", "REVIEW_REQUIRED");
                body.put("guardrail_failure_code", "CANDIDATE_HUMAN_REVIEW_REQUIRED");
                body.put("result_status", "BLOCKED");
                body.put("job_status", "WAITING_HUMAN_CONFIRMATION");
            }
            default -> {
                body.put("guardrail_decision", "REJECT");
                body.put("guardrail_failure_code", "CANDIDATE_LOW_QUALITY_REJECTED");
                body.put("result_status", "REJECTED");
                body.put("job_status", "FAILED");
            }
        }
        body.put("guardrail_required", true);
        body.put("direct_publish_blocked", !List.of("PASS", "PASS_PARTIAL").contains(body.get("guardrail_decision")));
        return body;
    }

    private String primarySlot(String applicationType) {
        return switch (applicationType) {
            case "QA" -> "ANSWER_SUPPORT";
            case "RISK_ANALYSIS" -> "RISK_EVIDENCE";
            case "DIFF_EXTRACTION" -> "DIFF_DELTA";
            default -> "SUMMARY_FACT";
        };
    }

    private String evidenceSlot(String applicationType) {
        return switch (applicationType) {
            case "QA" -> "ANSWER_SUPPORT";
            case "RISK_ANALYSIS" -> "RISK_EVIDENCE";
            case "DIFF_EXTRACTION" -> "DIFF_DELTA";
            default -> "SUMMARY_FACT";
        };
    }

    private String candidateSourceDigest(List<SearchDocumentState> evidenceDocs, String documentVersionId, String applicationType) {
        return "sha256:" + Integer.toHexString((applicationType + ":" + documentVersionId + ":" + evidenceDocs.stream().map(SearchDocumentState::sourceVersionDigest).toList()).hashCode());
    }

    private Map<String, Object> candidateRankingSnapshotBody(CandidateRankingSnapshotState snapshot, List<Map<String, Object>> candidates) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ranking_snapshot_id", snapshot.rankingSnapshotId());
        body.put("ai_application_job_id", snapshot.aiApplicationJobId());
        body.put("application_type", snapshot.applicationType());
        body.put("contract_id", snapshot.contractId());
        body.put("document_version_id", snapshot.documentVersionId());
        body.put("source_digest", snapshot.sourceDigest());
        body.put("ranking_profile", Map.of("profile_code", snapshot.rankingProfileCode(), "profile_version", snapshot.rankingProfileVersion()));
        body.put("quality_profile", Map.of("profile_code", snapshot.qualityProfileCode(), "profile_version", snapshot.qualityProfileVersion()));
        body.put("snapshot_status", snapshot.snapshotStatus());
        body.put("semantic_candidates", candidates);
        body.put("slot_rankings", slotRankings(candidates));
        body.put("slot_governance", slotGovernance(candidates));
        body.put("selected_candidate_ref", listMapsFromJson(snapshot.selectedCandidateJson()));
        body.put("expires_at", snapshot.expiresAt());
        return body;
    }

    private Map<String, Object> slotRankings(List<Map<String, Object>> candidates) {
        Map<String, Object> quota = slotQuota();
        Map<String, Object> bySlot = new LinkedHashMap<>();
        for (String slot : List.of("SUMMARY_FACT", "ANSWER_SUPPORT", "RISK_EVIDENCE", "RISK_BASELINE", "DIFF_BASELINE", "DIFF_DELTA", "FIELD_VALUE", "RULE_OVERRIDE")) {
            int limit = ((Number) quota.get(slot)).intValue();
            List<Map<String, Object>> ranked = candidates.stream()
                    .filter(candidate -> slot.equals(text(candidate, "semantic_slot", "SUMMARY_FACT")))
                    .sorted(Comparator.comparing(candidate -> -doubleValue(candidate, "ranking_score", 0.0)))
                    .limit(limit)
                    .toList();
            bySlot.put(slot, ranked);
        }
        return bySlot;
    }

    private Map<String, Object> slotGovernance(List<Map<String, Object>> candidates) {
        long reserved = candidates.stream().filter(candidate -> "RESERVED".equals(candidate.get("elimination_status"))).count();
        long conflicted = candidates.stream().filter(candidate -> "CONFLICTED".equals(candidate.get("elimination_status"))).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("slot_quota", slotQuota());
        body.put("dedupe_summary", Map.of("reserved_count", reserved, "same_source_compression", reserved > 0));
        body.put("conflict_resolution_summary", Map.of("conflicted_count", conflicted, "resolution", conflicted > 0 ? "ESCALATE_TO_HUMAN" : "NO_CONFLICT"));
        body.put("strong_veto_rule_applied", conflicted > 0);
        body.put("explanation_summary", candidates.stream().limit(3).map(candidate -> text(candidate, "explanation_digest", "")).toList());
        return body;
    }

    private Map<String, Object> slotQuota() {
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("SUMMARY_FACT", 2);
        quota.put("ANSWER_SUPPORT", 2);
        quota.put("RISK_EVIDENCE", 2);
        quota.put("RISK_BASELINE", 2);
        quota.put("DIFF_BASELINE", 2);
        quota.put("DIFF_DELTA", 2);
        quota.put("FIELD_VALUE", 2);
        quota.put("RULE_OVERRIDE", 2);
        return quota;
    }

    private Map<String, Object> qualityEvaluationBody(QualityEvaluationReportState quality) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quality_evaluation_id", quality.qualityEvaluationId());
        body.put("ranking_snapshot_id", quality.rankingSnapshotId());
        body.put("application_type", quality.applicationType());
        body.put("quality_profile_code", quality.qualityProfileCode());
        body.put("quality_profile_version", quality.qualityProfileVersion());
        body.put("coverage_score", quality.coverageScore());
        body.put("citation_validity_score", quality.citationValidityScore());
        body.put("consistency_score", quality.consistencyScore());
        body.put("completeness_score", quality.completenessScore());
        body.put("publishability_score", quality.publishabilityScore());
        body.put("quality_tier", quality.qualityTier());
        body.put("release_decision", quality.releaseDecision());
        body.put("decision_reason_code_list", quality.decisionReasonCodes());
        return body;
    }

    private Map<String, Object> candidateWritebackGateBody(CandidateWritebackGateState gate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result_id", gate.resultId());
        body.put("ranking_snapshot_id", gate.rankingSnapshotId());
        body.put("quality_evaluation_id", gate.qualityEvaluationId());
        body.put("release_decision", gate.releaseDecision());
        body.put("writeback_gate_status", gate.writebackGateStatus());
        body.put("writeback_allowed_flag", gate.writebackAllowed());
        return body;
    }

    private boolean candidateDocumentVersionChanged(CandidateRankingSnapshotState snapshot) {
        if (snapshot.documentVersionId() == null) {
            return false;
        }
        DocumentVersionState version = requireDocumentVersion(snapshot.documentVersionId());
        DocumentAssetState asset = requireDocumentAsset(version.documentAssetId());
        return !snapshot.documentVersionId().equals(asset.currentVersionId());
    }

    private void supersedeCandidateSnapshotsForDocumentVersion(String documentVersionId, String traceId) {
        for (CandidateRankingSnapshotState snapshot : new ArrayList<>(candidateRankingSnapshots.values())) {
            if (documentVersionId != null && documentVersionId.equals(snapshot.documentVersionId()) && "READY".equals(snapshot.snapshotStatus())) {
                CandidateRankingSnapshotState superseded = snapshot.withStatus("SUPERSEDED");
                candidateRankingSnapshots.put(snapshot.rankingSnapshotId(), superseded);
                updateCandidateSnapshotStatusInResult(snapshot.rankingSnapshotId(), "SUPERSEDED");
                appendCandidateAudit("CANDIDATE_SNAPSHOT_SUPERSEDED", snapshot.aiApplicationJobId(), snapshot.rankingSnapshotId(), null, "SUPERSEDED", "document-center", traceId);
            }
        }
    }

    private void appendCandidateAudit(String actionType, String jobId, String rankingSnapshotId, String qualityEvaluationId, String resultStatus, String actorId, String traceId) {
        appendAiAudit(jobId, actionType, actorId, resultStatus, traceId);
    }

    private void updateAiResultStructuredPayload(String resultId, Map<String, Object> payload) {
        AiApplicationResultState result = aiApplicationResults.get(resultId);
        if (result != null) {
            Map<String, Object> copy = new LinkedHashMap<>(payload);
            result.structuredPayload().clear();
            result.structuredPayload().putAll(copy);
        }
        jdbcTemplate.update("update ia_ai_application_result set structured_payload_json = ? where result_id = ?", json(payload), resultId);
    }

    private CandidateRankingSnapshotState loadCandidateSnapshot(String snapshotId) {
        CandidateRankingSnapshotState cached = candidateRankingSnapshots.get(snapshotId);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select j.ai_application_job_id, j.application_type, j.contract_id, j.document_version_id, j.trace_id, r.structured_payload_json
                from ia_ai_application_job j
                join ia_ai_application_result r on r.ai_application_job_id = j.ai_application_job_id
                where j.result_context_id = ?
                """, snapshotId);
        if (rows.isEmpty()) {
            rows = jdbcTemplate.queryForList("""
                    select j.ai_application_job_id, j.application_type, j.contract_id, j.document_version_id, j.trace_id, r.structured_payload_json
                    from ia_ai_application_result r
                    left join ia_ai_application_job j on r.ai_application_job_id = j.ai_application_job_id
                    where r.structured_payload_json like ?
                    """, "%" + snapshotId + "%");
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> payload = mapFromJson(text(row, "STRUCTURED_PAYLOAD_JSON", text(row, "structured_payload_json", "{}")));
        Map<String, Object> ranking = map(payload.get("ranking_snapshot"));
        if (ranking.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> candidates = listMaps(ranking.get("semantic_candidates"));
        List<Map<String, Object>> selected = listMaps(ranking.get("selected_candidate_ref"));
        Map<String, Object> rankingProfile = map(ranking.get("ranking_profile"));
        Map<String, Object> qualityProfile = map(ranking.get("quality_profile"));
        CandidateRankingSnapshotState snapshot = new CandidateRankingSnapshotState(snapshotId,
                text(row, "AI_APPLICATION_JOB_ID", text(row, "ai_application_job_id", null)),
                text(ranking, "application_type", text(row, "APPLICATION_TYPE", text(row, "application_type", null))),
                text(ranking, "contract_id", text(row, "CONTRACT_ID", text(row, "contract_id", null))),
                text(ranking, "document_version_id", text(row, "DOCUMENT_VERSION_ID", text(row, "document_version_id", null))),
                text(ranking, "source_digest", "sha256:recovered"),
                text(rankingProfile, "profile_code", "CANDIDATE_RANKING_BASELINE"),
                text(rankingProfile, "profile_version", activeCandidateProfileVersion),
                text(qualityProfile, "profile_code", "CANDIDATE_QUALITY_BASELINE"),
                text(qualityProfile, "profile_version", activeCandidateProfileVersion),
                text(ranking, "snapshot_status", "READY"),
                json(selected), json(candidates), text(ranking, "expires_at", Instant.now().plusSeconds(900).toString()),
                text(row, "TRACE_ID", text(row, "trace_id", null)), Instant.now().toString());
        candidateRankingSnapshots.put(snapshotId, snapshot);
        return snapshot;
    }

    private CandidateWritebackGateState loadCandidateWritebackGate(String resultId) {
        CandidateWritebackGateState cached = candidateWritebackGates.get(resultId);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select structured_payload_json from ia_ai_application_result where result_id = ?", resultId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> payload = mapFromJson(text(rows.get(0), "STRUCTURED_PAYLOAD_JSON", text(rows.get(0), "structured_payload_json", "{}")));
        Map<String, Object> gatePayload = map(payload.get("writeback_gate"));
        if (gatePayload.isEmpty()) {
            return null;
        }
        CandidateWritebackGateState gate = new CandidateWritebackGateState(resultId,
                text(gatePayload, "ranking_snapshot_id", text(payload, "ranking_snapshot_id", null)),
                text(gatePayload, "quality_evaluation_id", text(payload, "quality_evaluation_id", null)),
                text(gatePayload, "release_decision", text(payload, "release_decision", null)),
                text(gatePayload, "writeback_gate_status", "BLOCKED"),
                bool(gatePayload, "writeback_allowed_flag", false),
                null, Instant.now().toString());
        candidateWritebackGates.put(resultId, gate);
        return gate;
    }

    private void updateCandidateSnapshotStatusInResult(String snapshotId, String status) {
        CandidateRankingSnapshotState cached = candidateRankingSnapshots.get(snapshotId);
        if (cached != null) {
            candidateRankingSnapshots.put(snapshotId, cached.withStatus(status));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select result_id, structured_payload_json from ia_ai_application_result where structured_payload_json like ?", "%" + snapshotId + "%");
        for (Map<String, Object> row : rows) {
            String resultId = text(row, "RESULT_ID", text(row, "result_id", null));
            Map<String, Object> payload = mapFromJson(text(row, "STRUCTURED_PAYLOAD_JSON", text(row, "structured_payload_json", "{}")));
            Map<String, Object> ranking = new LinkedHashMap<>(map(payload.get("ranking_snapshot")));
            if (!ranking.isEmpty()) {
                ranking.put("snapshot_status", status);
                payload.put("ranking_snapshot", ranking);
            }
            jdbcTemplate.update("update ia_ai_application_result set structured_payload_json = ? where result_id = ?", json(payload), resultId);
        }
    }

    private AiApplicationJobState findAiJobByIdempotency(String applicationType, String contractId, String idempotencyKey) {
        return aiApplicationJobs.values().stream()
                .filter(job -> applicationType.equals(job.applicationType()))
                .filter(job -> contractId.equals(job.contractId()))
                .filter(job -> idempotencyKey.equals(job.idempotencyKey()))
                .findFirst()
                .orElse(null);
    }

    private AiApplicationResultState requireAiResultByJob(String jobId) {
        return aiApplicationResults.values().stream()
                .filter(result -> jobId.equals(result.aiApplicationJobId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ai result 不存在: " + jobId));
    }

    private ProtectedResultSnapshotState protectedSnapshotByJob(String jobId) {
        return protectedResultSnapshots.values().stream()
                .filter(snapshot -> jobId.equals(snapshot.aiApplicationJobId()))
                .findFirst()
                .orElse(null);
    }

    private List<SearchDocumentState> aiEvidenceDocuments(String contractId, String documentVersionId, String orgScope) {
        return defaultSearchDocuments().stream()
                .filter(document -> contractId.equals(document.contractId()))
                .filter(document -> documentVersionId == null || document.documentVersionId() == null || documentVersionId.equals(document.documentVersionId()))
                .filter(document -> orgScope == null || orgScope.isBlank() || orgScope.equals(ownerOrg(document)))
                .limit(12)
                .toList();
    }

    private List<Map<String, Object>> buildEvidenceSegments(List<SearchDocumentState> documents, Map<String, Object> request) {
        int maxCount = intValue(request, "max_evidence_segment_count", 8);
        int singleCap = intValue(request, "single_segment_token_cap", 120);
        List<Map<String, Object>> segments = new ArrayList<>();
        int index = 1;
        for (SearchDocumentState document : documents.stream().limit(maxCount).toList()) {
            String text = document.bodyText();
            boolean trimmed = text.length() > singleCap;
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("evidence_segment_id", "ev-" + UUID.randomUUID());
            segment.put("source_type", switch (document.docType()) {
                case "DOCUMENT" -> "DOCUMENT_SNIPPET";
                case "OCR" -> "OCR_BLOCK";
                case "CLAUSE" -> "CLAUSE_REF";
                default -> "CONTRACT_SUMMARY";
            });
            segment.put("source_object_id", document.sourceObjectId());
            segment.put("contract_id", document.contractId());
            segment.put("document_version_id", document.documentVersionId());
            segment.put("page_no", 1);
            segment.put("citation_ref", "cite-" + index);
            segment.put("segment_text", trimmed ? text.substring(0, singleCap) : text);
            segment.put("token_cost", Math.max(1, text.length() / 2));
            segment.put("trimmed", trimmed);
            segment.put("source_anchor", mapFromJson(document.sourceAnchorJson()));
            segments.add(segment);
            index++;
        }
        return segments;
    }

    private boolean canFitEvidenceBudget(List<Map<String, Object>> evidenceSegments, Map<String, Object> request) {
        int maxInputTokens = intValue(request, "max_input_tokens", 4096);
        int reservedGuardrailTokens = intValue(request, "reserved_guardrail_tokens", 512);
        int reservedCitationTokens = intValue(request, "reserved_citation_tokens", 128);
        int requiredEvidenceTokens = evidenceSegments.stream()
                .map(segment -> segment.get("token_cost"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .sum();
        return !evidenceSegments.isEmpty() && maxInputTokens - reservedGuardrailTokens - reservedCitationTokens >= requiredEvidenceTokens;
    }

    private Map<String, Object> buildAiContextEnvelope(String resultContextId, String jobId, String contextAssemblyJobId, String applicationType,
                                                       ContractState contract, List<SearchDocumentState> evidenceDocs,
                                                       List<Map<String, Object>> evidenceSegments, Map<String, Object> request) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("result_context_id", resultContextId);
        envelope.put("ai_application_job_id", jobId);
        envelope.put("context_assembly_job_id", contextAssemblyJobId);
        envelope.put("application_type", applicationType);
        envelope.put("task_intent_layer", Map.of(
                "application_type", applicationType,
                "target_language", text(request, "target_language", "zh-CN"),
                "output_mode", text(request, "output_mode", "STRUCTURED"),
                "question", text(request, "question", "")));
        envelope.put("contract_anchor_layer", Map.of(
                "contract_id", contract.contractId(),
                "contract_no", contract.contractNo(),
                "contract_name", contract.contractName(),
                "contract_status", contract.contractStatus(),
                "owner_org_unit_id", contract.ownerOrgUnitId()));
        envelope.put("document_evidence_layer", Map.of(
                "document_version_id", text(request, "document_version_id", null),
                "evidence_segment_list", evidenceSegments));
        envelope.put("retrieval_layer", Map.of(
                "result_set_id", "ai-search-snapshot-" + UUID.randomUUID(),
                "search_doc_ids", evidenceDocs.stream().map(SearchDocumentState::searchDocId).toList(),
                "stable_source_count", evidenceDocs.size()));
        envelope.put("semantic_reference_layer", Map.of(
                "semantic_reference_list", List.of(Map.of(
                        "semantic_ref_id", "sem-" + contract.contractId(),
                        "clause_version_id", "clause-ver-ai",
                        "risk_tag", "STANDARD_CONTRACT_RISK"))));
        envelope.put("guardrail_layer", Map.of(
                "guardrail_profile_code", "AI_GUARDRAIL_BASELINE",
                "schema_profile_code", "AI_SCHEMA_" + applicationType,
                "citation_required", true,
                "sensitive_masking_profile_code", "CMP_DEFAULT_MASKING",
                "human_confirmation_threshold", "HIGH_RISK_OR_CONFLICT"));
        envelope.put("token_budget_snapshot", Map.of(
                "max_input_tokens", 4096,
                "max_output_tokens", 1024,
                "max_evidence_segment_count", intValue(request, "max_evidence_segment_count", 8),
                "reserved_guardrail_tokens", 512));
        envelope.put("degradation_actions", evidenceSegments.stream().filter(segment -> Boolean.TRUE.equals(segment.get("trimmed"))).findAny().isPresent()
                ? List.of("TRIM_LONG_EVIDENCE_SEGMENT") : List.of());
        envelope.put("source_digest", "sha256:" + Integer.toHexString(evidenceSegments.toString().hashCode()));
        envelope.put("assembled_at", Instant.now().toString());
        return envelope;
    }

    private GuardrailOutcome evaluateAiGuardrail(String applicationType, List<Map<String, Object>> evidenceSegments, Map<String, Object> request) {
        Map<String, Object> agentOutput = map(request.get("agent_output"));
        if (bool(request, "simulate_schema_invalid_output", false)) {
            return new GuardrailOutcome("BLOCK", "AI_OUTPUT_SCHEMA_INVALID", Map.of("summary", "输出结构不符合任务 schema"), List.of(), List.of("SCHEMA_INVALID"));
        }
        if (bool(request, "simulate_no_citation_output", false)) {
            return new GuardrailOutcome("BLOCK", "AI_CITATION_MISSING", Map.of("summary", "输出缺少引用"), List.of(), List.of("NO_SOURCE_CONCLUSION"));
        }
        if (bool(request, "simulate_sensitive_output", false)) {
            return new GuardrailOutcome("BLOCK", "AI_SENSITIVE_INFORMATION_BLOCKED", Map.of("summary", "输出包含未脱敏高敏信息"), List.of(), List.of("SENSITIVE_INFORMATION"));
        }
        if (!agentOutput.isEmpty() && !hasRequiredSchema(applicationType, agentOutput)) {
            return new GuardrailOutcome("BLOCK", "AI_OUTPUT_SCHEMA_INVALID", agentOutput, List.of(), List.of("SCHEMA_INVALID"));
        }
        if (!agentOutput.isEmpty() && containsSensitiveInformation(agentOutput)) {
            return new GuardrailOutcome("BLOCK", "AI_SENSITIVE_INFORMATION_BLOCKED", Map.of("summary", "输出包含未脱敏高敏信息"), List.of(), List.of("SENSITIVE_INFORMATION"));
        }
        List<Map<String, Object>> citations = resolveOutputCitations(agentOutput, evidenceSegments, applicationType);
        if (!agentOutput.isEmpty() && citations.isEmpty()) {
            return new GuardrailOutcome("BLOCK", "AI_CITATION_MISSING", agentOutput, List.of(), List.of("NO_SOURCE_CONCLUSION"));
        }
        if (!agentOutput.isEmpty() && bool(agentOutput, "conflict_detected", false)) {
            return new GuardrailOutcome("PASS_PARTIAL", "AI_CONFLICT_DOWNGRADED", agentOutput, citations, List.of("CONFLICT_DOWNGRADED"));
        }
        if (!agentOutput.isEmpty() && "HIGH".equals(text(agentOutput, "risk_level", null))) {
            return new GuardrailOutcome("REVIEW_REQUIRED", "AI_HIGH_RISK_REQUIRES_HUMAN", agentOutput, citations, List.of("HIGH_RISK"));
        }
        if (agentOutput.isEmpty()) {
            citations = evidenceSegments.stream()
                .limit("RISK_ANALYSIS".equals(applicationType) || "DIFF_EXTRACTION".equals(applicationType) ? 2 : 1)
                .map(segment -> Map.of("evidence_segment_id", segment.get("evidence_segment_id"), "citation_ref", segment.get("citation_ref")))
                .toList();
        }
        if (bool(request, "simulate_high_risk", false)) {
            return new GuardrailOutcome("REVIEW_REQUIRED", "AI_HIGH_RISK_REQUIRES_HUMAN", Map.of("risk_level", "HIGH", "summary", "发现高风险条款，需人工确认"), citations, List.of("HIGH_RISK"));
        }
        if (bool(request, "simulate_conflict", false)) {
            return new GuardrailOutcome("PASS_PARTIAL", "AI_CONFLICT_DOWNGRADED", Map.of("summary", "冲突内容已降级为部分结果"), citations, List.of("CONFLICT_DOWNGRADED"));
        }
        return new GuardrailOutcome("PASS", null, agentOutput.isEmpty() ? Map.of("summary", "已基于受控证据生成") : agentOutput, citations, List.of());
    }

    private boolean hasRequiredSchema(String applicationType, Map<String, Object> agentOutput) {
        return switch (applicationType) {
            case "QA" -> agentOutput.containsKey("answer");
            case "RISK_ANALYSIS" -> agentOutput.containsKey("risk_level") && agentOutput.containsKey("summary");
            case "DIFF_EXTRACTION" -> agentOutput.containsKey("summary") || agentOutput.containsKey("difference_type");
            default -> agentOutput.containsKey("summary") || agentOutput.containsKey("answer");
        };
    }

    private boolean containsSensitiveInformation(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return mapValue.values().stream().anyMatch(this::containsSensitiveInformation);
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().anyMatch(this::containsSensitiveInformation);
        }
        String text = value == null ? "" : value.toString();
        return text.matches(".*\\b\\d{17}[0-9Xx]\\b.*") || text.matches(".*\\b\\d{16,19}\\b.*");
    }

    private List<Map<String, Object>> resolveOutputCitations(Map<String, Object> agentOutput, List<Map<String, Object>> evidenceSegments, String applicationType) {
        List<Object> requested = listObjects(agentOutput.get("citation_list"));
        if (requested.isEmpty()) {
            return List.of();
        }
        int limit = "RISK_ANALYSIS".equals(applicationType) || "DIFF_EXTRACTION".equals(applicationType) ? 2 : 1;
        if (requested.stream().anyMatch(value -> "AUTO".equals(value.toString()))) {
            return evidenceSegments.stream()
                    .limit(limit)
                    .map(segment -> Map.of("evidence_segment_id", segment.get("evidence_segment_id"), "citation_ref", segment.get("citation_ref")))
                    .toList();
        }
        Set<String> known = evidenceSegments.stream().map(segment -> segment.get("evidence_segment_id").toString()).collect(java.util.stream.Collectors.toSet());
        return requested.stream()
                .map(Object::toString)
                .filter(known::contains)
                .map(id -> evidenceSegments.stream().filter(segment -> id.equals(segment.get("evidence_segment_id"))).findFirst().orElseThrow())
                .map(segment -> Map.of("evidence_segment_id", segment.get("evidence_segment_id"), "citation_ref", segment.get("citation_ref")))
                .toList();
    }

    private AgentOsBinding createAgentOsAiTask(String jobId, String applicationType, String contractId, String resultContextId, String actorId,
                                                String idempotencyKey, String traceId, boolean timeout) {
        Map<String, Object> task = agentOsGateway.createTask(idempotencyKey, Map.of(
                "task_type", applicationType,
                "task_source", "INTELLIGENT_APPLICATIONS",
                "requester_type", "USER",
                "requester_id", actorId,
                "specialized_agent_code", "CONTRACT_REVIEW_AGENT",
                "input_context", Map.of("business_module", "intelligent-applications", "object_type", "CONTRACT", "object_id", contractId),
                "input_payload", Map.of("question", "执行智能辅助应用任务", "result_context_id", resultContextId, "scenario", timeout ? "TIMEOUT" : "NORMAL"),
                "max_loop_count", 1,
                "trace_id", traceId));
        return new AgentOsBinding(text(task, "task_id", null), text(task, "run_id", null), text(task, "result_id", null), timeout ? "AGENT_OS_TIMEOUT" : null);
    }

    private Map<String, Object> createAiHumanConfirmation(String agentTaskId, String agentResultId, String contractId, String applicationType, Map<String, Object> payload,
                                                          List<Map<String, Object>> evidenceSegments, String actorId, String traceId) {
        Map<String, Object> decisionInput = new LinkedHashMap<>();
        decisionInput.put("application_type", applicationType);
        decisionInput.put("result_summary", payload);
        decisionInput.put("evidence_bundle_ref", "ai-evidence://" + agentTaskId);
        decisionInput.put("recommended_actions", List.of("APPROVE", "REJECT", "REQUEST_CHANGES"));
        decisionInput.put("evidence_count", evidenceSegments.size());
        Map<String, Object> confirmation = agentOsGateway.createHumanConfirmation(Map.of(
                "source_task_id", agentTaskId,
                "source_result_id", agentResultId,
                "confirmation_type", "AI_OUTPUT_REVIEW",
                "business_module", "intelligent-applications",
                "object_type", "CONTRACT",
                "object_id", contractId,
                "requested_by", actorId,
                "decision_input", decisionInput,
                "trace_id", traceId));
        Map<String, Object> body = new LinkedHashMap<>(confirmation);
        body.put("decision_input", decisionInput);
        return body;
    }

    private AiApplicationJobState persistAiJob(String jobId, String applicationType, String contractId, String documentVersionId, String jobStatus,
                                               String contextAssemblyJobId, String resultContextId, String agentTaskId, String agentResultId,
                                               String idempotencyKey, String scopeDigest, String failureCode, String failureReason, String traceId) {
        AiApplicationJobState job = new AiApplicationJobState(jobId, applicationType, contractId, documentVersionId, jobStatus, contextAssemblyJobId,
                resultContextId, agentTaskId, agentResultId, idempotencyKey, scopeDigest, failureCode, failureReason, traceId, Instant.now().toString(), Instant.now().toString());
        aiApplicationJobs.put(jobId, job);
        jdbcTemplate.update("""
                insert into ia_ai_application_job
                (ai_application_job_id, application_type, contract_id, document_version_id, job_status, context_assembly_job_id, result_context_id,
                 agent_task_id, agent_result_id, idempotency_key, scope_digest, failure_code, failure_reason, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, jobId, applicationType, contractId, documentVersionId, jobStatus, contextAssemblyJobId, resultContextId, agentTaskId, agentResultId,
                idempotencyKey, scopeDigest, failureCode, failureReason, traceId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return job;
    }

    private void persistAiContextEnvelope(String resultContextId, String jobId, String contextAssemblyJobId, String applicationType, String contractId, Map<String, Object> envelope) {
        aiContextEnvelopes.put(resultContextId, envelope);
        jdbcTemplate.update("""
                insert into ia_ai_context_envelope
                (result_context_id, ai_application_job_id, context_assembly_job_id, application_type, contract_id, envelope_json, source_digest,
                 budget_snapshot_json, degradation_action_json, guardrail_profile_code, assembled_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'AI_GUARDRAIL_BASELINE', ?)
                """, resultContextId, jobId, contextAssemblyJobId, applicationType, contractId, json(envelope), text(envelope, "source_digest", "sha256:empty"),
                json(envelope.get("token_budget_snapshot")), json(envelope.get("degradation_actions")), Timestamp.from(Instant.now()));
    }

    private AiApplicationResultState createAiResult(String jobId, String applicationType, String agentTaskId, String agentResultId, String resultStatus,
                                                    String decision, String failureCode, boolean confirmationRequired, boolean writebackAllowed,
                                                    Map<String, Object> payload, List<Map<String, Object>> citations, List<String> riskFlags) {
        String resultId = "ai-result-" + UUID.randomUUID();
        double coverage = citations.isEmpty() ? 0.0 : 1.0;
        AiApplicationResultState result = new AiApplicationResultState(resultId, jobId, agentTaskId, agentResultId, applicationType, resultStatus,
                payload, citations, coverage, decision, failureCode, confirmationRequired, writebackAllowed, riskFlags, Instant.now().toString());
        aiApplicationResults.put(resultId, result);
        jdbcTemplate.update("""
                insert into ia_ai_application_result
                (result_id, ai_application_job_id, agent_task_id, agent_result_id, application_type, result_status, structured_payload_json,
                 citation_list_json, evidence_coverage_ratio, guardrail_decision, guardrail_failure_code, confirmation_required_flag,
                 writeback_allowed_flag, risk_flag_list_json, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, resultId, jobId, agentTaskId, agentResultId, applicationType, resultStatus, json(payload), json(citations), coverage, decision, failureCode,
                confirmationRequired, writebackAllowed, json(riskFlags), Timestamp.from(Instant.now()));
        return result;
    }

    private ProtectedResultSnapshotState createProtectedSnapshot(String jobId, String resultId, String agentTaskId, String agentResultId, String decision,
                                                                 String failureCode, boolean confirmationRequired, Map<String, Object> payload) {
        String snapshotId = "ai-protected-" + UUID.randomUUID();
        ProtectedResultSnapshotState snapshot = new ProtectedResultSnapshotState(snapshotId, jobId, resultId, agentTaskId, agentResultId, decision, failureCode,
                confirmationRequired, "protected://ai-result/" + snapshotId, payload, Instant.now().plusSeconds(86400).toString(), Instant.now().toString());
        protectedResultSnapshots.put(snapshotId, snapshot);
        jdbcTemplate.update("""
                insert into ia_protected_result_snapshot
                (protected_result_snapshot_id, ai_application_job_id, result_id, agent_task_id, agent_result_id, guardrail_decision,
                 guardrail_failure_code, confirmation_required_flag, protected_payload_ref, protected_payload_json, expires_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshotId, jobId, resultId, agentTaskId, agentResultId, decision, failureCode, confirmationRequired,
                snapshot.protectedPayloadRef(), json(payload), Timestamp.from(Instant.parse(snapshot.expiresAt())), Timestamp.from(Instant.now()));
        return snapshot;
    }

    private Map<String, Object> createStandaloneProtectedSnapshot(String applicationType, String contractId, String decision, String failureCode, Map<String, Object> request) {
        String jobId = "ai-rejected-" + UUID.randomUUID();
        ProtectedResultSnapshotState snapshot = createProtectedSnapshot(jobId, null, null, null, decision, failureCode, false,
                Map.of("application_type", applicationType, "contract_id", contractId, "reason", failureCode));
        appendAiAudit(jobId, "AI_PRE_EXECUTION_REJECTED", text(request, "actor_id", "system"), decision, text(request, "trace_id", null));
        return protectedSnapshotBody(snapshot);
    }

    private Map<String, Object> aiJobResponse(AiApplicationJobState job, Map<String, Object> envelope, AiApplicationResultState result,
                                              ProtectedResultSnapshotState snapshot, Map<String, Object> confirmation, AgentOsBinding agent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ai_application_job_id", job.aiApplicationJobId());
        body.put("application_type", job.applicationType());
        body.put("contract_id", job.contractId());
        body.put("job_status", job.jobStatus());
        Map<String, Object> agentOs = new LinkedHashMap<>();
        agentOs.put("agent_task_id", agent.taskId());
        agentOs.put("agent_result_id", agent.resultId());
        agentOs.put("failure_code", agent.failureCode());
        body.put("agent_os", agentOs);
        body.put("context_envelope", envelope);
        body.put("guarded_result", aiResultBody(result));
        if (snapshot != null) {
            body.put("protected_result_snapshot_id", snapshot.protectedResultSnapshotId());
            body.put("protected_result_snapshot", protectedSnapshotBody(snapshot));
        }
        if (confirmation != null) {
            body.put("human_confirmation", confirmation);
        }
        return body;
    }

    private Map<String, Object> aiResultBody(AiApplicationResultState result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result_id", result.resultId());
        body.put("result_status", result.resultStatus());
        body.put("structured_payload", result.structuredPayload());
        body.put("citation_list", result.citationList());
        body.put("evidence_coverage_ratio", result.evidenceCoverageRatio());
        body.put("guardrail_decision", result.guardrailDecision());
        body.put("guardrail_failure_code", result.guardrailFailureCode());
        body.put("confirmation_required_flag", result.confirmationRequiredFlag());
        body.put("writeback_allowed_flag", result.writebackAllowedFlag());
        body.put("risk_flag_list", result.riskFlagList());
        return body;
    }

    private Map<String, Object> protectedSnapshotBody(ProtectedResultSnapshotState snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("protected_result_snapshot_id", snapshot.protectedResultSnapshotId());
        body.put("ai_application_job_id", snapshot.aiApplicationJobId());
        body.put("result_id", snapshot.resultId());
        body.put("agent_task_id", snapshot.agentTaskId());
        body.put("agent_result_id", snapshot.agentResultId());
        body.put("guardrail_decision", snapshot.guardrailDecision());
        body.put("guardrail_failure_code", snapshot.guardrailFailureCode());
        body.put("confirmation_required_flag", snapshot.confirmationRequiredFlag());
        body.put("protected_payload_ref", snapshot.protectedPayloadRef());
        body.put("expires_at", snapshot.expiresAt());
        return body;
    }

    private void appendAiAudit(String jobId, String actionType, String actorId, String status, String traceId) {
        jdbcTemplate.update("""
                insert into ia_ai_audit_event
                (audit_event_id, ai_application_job_id, action_type, actor_id, result_status, trace_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "ai-audit-" + UUID.randomUUID(), jobId, actionType, actorId, status, traceId, Timestamp.from(Instant.now()));
    }

    private SearchSourceEnvelopeState buildSearchSourceEnvelope(String docType, Map<String, Object> request) {
        String contractId = text(request, "contract_id", null);
        String documentVersionId = text(request, "document_version_id", null);
        String ocrResultId = text(request, "ocr_result_aggregate_id", null);
        ContractState contract = contractId == null ? null : requireContract(contractId);
        return switch (docType) {
            case "CONTRACT" -> contractEnvelope(requireContract(contractId));
            case "DOCUMENT" -> documentEnvelope(requireDocumentVersion(documentVersionId));
            case "OCR" -> ocrEnvelope(requireOcrResult(ocrResultId));
            case "CLAUSE" -> clauseEnvelope(contract == null ? requireContract(contractId) : contract);
            default -> throw new IllegalArgumentException("不支持的搜索来源类型: " + docType);
        };
    }

    private SearchSourceEnvelopeState contractEnvelope(ContractState contract) {
        String digest = versionDigest("CONTRACT", contract.contractId(), contract.contractName(), contract.contractStatus());
        return new SearchSourceEnvelopeState("search-env-" + UUID.randomUUID(), "CONTRACT", contract.contractId(), digest, contract.contractId(), null, null, null,
                json(Map.of("contract_id", contract.contractId(), "target", "CONTRACT_DETAIL")), contract.contractName(), contract.contractName() + " " + contract.contractNo(),
                contract.contractNo() + " " + classificationMasterLink(contract.contractId()).get("category_path"), contract.ownerOrgUnitId(), "zh-CN", "ADMITTED", Instant.now().toString());
    }

    private SearchSourceEnvelopeState documentEnvelope(DocumentVersionState version) {
        DocumentAssetState asset = requireDocumentAsset(version.documentAssetId());
        ContractState contract = requireContract(asset.ownerId());
        String digest = versionDigest("DOCUMENT", version.documentVersionId(), asset.documentTitle(), version.versionLabel());
        return new SearchSourceEnvelopeState("search-env-" + UUID.randomUUID(), "DOCUMENT", version.documentVersionId(), digest, asset.ownerId(), asset.documentAssetId(), version.documentVersionId(), null,
                json(Map.of("contract_id", asset.ownerId(), "document_asset_id", asset.documentAssetId(), "document_version_id", version.documentVersionId())), asset.documentTitle(),
                asset.documentTitle() + " " + contract.contractName(), asset.documentRole() + " " + version.versionLabel(), contract.ownerOrgUnitId(), "zh-CN", "ADMITTED", Instant.now().toString());
    }

    private SearchSourceEnvelopeState ocrEnvelope(OcrResultState result) {
        ContractState contract = requireContract(result.contractId());
        String text = result.textLayer().stream().map(page -> text(page, "text", "")).reduce((left, right) -> left + " " + right).orElse("");
        String digest = versionDigest("OCR", result.ocrResultAggregateId(), result.contentFingerprint(), result.resultStatus());
        return new SearchSourceEnvelopeState("search-env-" + UUID.randomUUID(), "OCR", result.ocrResultAggregateId(), digest, result.contractId(), result.documentAssetId(), result.documentVersionId(), null,
                json(Map.of("contract_id", result.contractId(), "document_version_id", result.documentVersionId(), "ocr_result_aggregate_id", result.ocrResultAggregateId(), "page_no", 1)),
                contract.contractName() + " OCR", text, "OCR " + result.qualityProfileCode(), contract.ownerOrgUnitId(), "zh-CN", result.defaultConsumable() ? "ADMITTED" : "SKIPPED", Instant.now().toString());
    }

    private SearchSourceEnvelopeState clauseEnvelope(ContractState contract) {
        String clauseVersionId = "clause-ver-" + text(contractSemanticReferenceRefs.getOrDefault(contract.contractId(), Map.of()), "clause_library_code", "clause-lib-default");
        String body = contract.contractName() + " 标准条款 风险标签 适用范围";
        String digest = versionDigest("CLAUSE", clauseVersionId, body, "ACTIVE");
        return new SearchSourceEnvelopeState("search-env-" + UUID.randomUUID(), "CLAUSE", clauseVersionId, digest, contract.contractId(), null, null, clauseVersionId,
                json(Map.of("contract_id", contract.contractId(), "clause_version_id", clauseVersionId)), "标准条款", body, "条款 风险 语义", contract.ownerOrgUnitId(), "zh-CN", "ADMITTED", Instant.now().toString());
    }

    private SearchDocumentState mapSearchDocument(SearchSourceEnvelopeState envelope, int generation) {
        String id = searchDocId(envelope.docType(), envelope.sourceObjectId(), envelope.sourceVersionDigest());
        String filter = json(Map.of("owner_org_unit_id", envelope.ownerOrgUnitId(), "doc_type", envelope.docType(), "contract_id", envelope.contractId()));
        String sort = json(Map.of("updated_at", Instant.now().toString(), "score", 1));
        String visibility = json(Map.of("owner_org_unit_id", envelope.ownerOrgUnitId(), "contract_id", envelope.contractId()));
        return new SearchDocumentState(id, envelope.docType(), envelope.sourceObjectId(), envelope.sourceAnchorJson(), envelope.sourceVersionDigest(), envelope.contractId(),
                envelope.documentAssetId(), envelope.documentVersionId(), envelope.semanticRefId(), envelope.titleText(), envelope.bodyText(), envelope.keywordText(),
                filter, sort, envelope.localeCode(), visibility, "ADMITTED".equals(envelope.admissionStatus()) ? "ACTIVE" : "HIDDEN", generation, Instant.now().toString());
    }

    private List<SearchDocumentState> searchCandidates(Map<String, Object> request, String orgScope, String deniedObjects, boolean degraded) {
        String query = text(request, "query_text", "").toLowerCase();
        String matchMode = text(request, "match_mode", "FUZZY");
        Set<String> scopes = new HashSet<>(listStrings(request.get("scope_list")));
        if (scopes.isEmpty()) {
            scopes.addAll(List.of("CONTRACT", "DOCUMENT", "OCR", "CLAUSE"));
        }
        Set<String> denied = Set.of(deniedObjects == null || deniedObjects.isBlank() ? new String[0] : deniedObjects.split(","));
        Map<String, Object> filter = map(request.get("filter_payload"));
        List<SearchDocumentState> matched = defaultSearchDocuments().stream()
                .filter(document -> scopes.contains(document.docType()))
                .filter(document -> !degraded || "CONTRACT".equals(document.docType()))
                .filter(document -> orgScope == null || orgScope.isBlank() || orgScope.equals(ownerOrg(document)))
                .filter(document -> !denied.contains(document.contractId()))
                .filter(document -> matchesSearchFilter(document, filter))
                .filter(document -> matchesSearchQuery(document, query, matchMode))
                .sorted(searchComparator(text(request, "sort_by", "updated_at"), text(request, "sort_order", "desc")))
                .toList();
        return matched;
    }

    private List<SearchDocumentState> defaultSearchDocuments() {
        Map<String, SearchDocumentState> latestByDocId = new LinkedHashMap<>();
        for (SearchDocumentState document : searchDocuments.values()) {
            if (!"ACTIVE".equals(document.exposureStatus()) || document.rebuildGeneration() > activeSearchGeneration) {
                continue;
            }
            SearchDocumentState existing = latestByDocId.get(document.searchDocId());
            if (existing == null || document.rebuildGeneration() > existing.rebuildGeneration()) {
                latestByDocId.put(document.searchDocId(), document);
            }
        }
        return new ArrayList<>(latestByDocId.values());
    }

    private boolean matchesSearchQuery(SearchDocumentState document, String query, String matchMode) {
        if (query.isBlank()) {
            return true;
        }
        if ("EXACT".equals(matchMode)) {
            return document.titleText().equalsIgnoreCase(query)
                    || document.sourceObjectId().equalsIgnoreCase(query)
                    || document.keywordText().equalsIgnoreCase(query);
        }
        return (document.titleText() + " " + document.bodyText() + " " + document.keywordText()).toLowerCase().contains(query);
    }

    private boolean matchesSearchFilter(SearchDocumentState document, Map<String, Object> filter) {
        return text(filter, "owner_org_unit_id", ownerOrg(document)).equals(ownerOrg(document))
                && text(filter, "contract_id", document.contractId()).equals(document.contractId())
                && text(filter, "doc_type", document.docType()).equals(document.docType())
                && text(filter, "locale_code", document.localeCode()).equals(document.localeCode());
    }

    private Comparator<SearchDocumentState> searchComparator(String sortBy, String sortOrder) {
        Comparator<SearchDocumentState> comparator = switch (sortBy) {
            case "title_text" -> Comparator.comparing(SearchDocumentState::titleText).thenComparing(SearchDocumentState::searchDocId);
            case "doc_type" -> Comparator.comparing(SearchDocumentState::docType).thenComparing(SearchDocumentState::searchDocId);
            default -> Comparator.comparing(SearchDocumentState::indexedAt).thenComparing(SearchDocumentState::searchDocId);
        };
        return "asc".equalsIgnoreCase(sortOrder) ? comparator : comparator.reversed();
    }

    private Map<String, Object> searchResultView(SearchDocumentState document, String permissions) {
        Map<String, Object> body = searchDocumentBody(document, permissions != null && permissions.contains("SEARCH_VIEW_BODY"));
        body.remove("visibility_scope_json");
        body.remove("filter_payload_json");
        body.remove("sort_payload_json");
        return body;
    }

    private Map<String, Object> searchDocumentBody(SearchDocumentState document, boolean includeBody) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("search_doc_id", document.searchDocId());
        body.put("doc_type", document.docType());
        body.put("source_object_id", document.sourceObjectId());
        body.put("source_version_digest", document.sourceVersionDigest());
        body.put("contract_id", document.contractId());
        body.put("document_asset_id", document.documentAssetId());
        body.put("document_version_id", document.documentVersionId());
        body.put("semantic_ref_id", document.semanticRefId());
        body.put("title_text", document.titleText());
        body.put("source_anchor", mapFromJson(document.sourceAnchorJson()));
        if (includeBody) {
            body.put("body_snippet", document.bodyText());
        }
        body.put("locale_code", document.localeCode());
        body.put("exposure_status", document.exposureStatus());
        body.put("rebuild_generation", document.rebuildGeneration());
        return body;
    }

    private Map<String, Object> searchSourceEnvelopeBody(SearchSourceEnvelopeState envelope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("envelope_id", envelope.envelopeId());
        body.put("doc_type", envelope.docType());
        body.put("source_object_id", envelope.sourceObjectId());
        body.put("source_version_digest", envelope.sourceVersionDigest());
        body.put("contract_id", envelope.contractId());
        body.put("document_asset_id", envelope.documentAssetId());
        body.put("document_version_id", envelope.documentVersionId());
        body.put("semantic_ref_id", envelope.semanticRefId());
        body.put("source_anchor", mapFromJson(envelope.sourceAnchorJson()));
        body.put("admission_status", envelope.admissionStatus());
        return body;
    }

    private Map<String, Object> searchResultSetBody(SearchResultSetState resultSet, List<Map<String, Object>> items, Map<String, Object> facets) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result_set_id", resultSet.resultSetId());
        body.put("result_status", resultSet.resultStatus());
        body.put("items", items);
        body.put("facets", facets);
        body.put("total", resultSet.total());
        body.put("ranking_profile_code", resultSet.rankingProfileCode());
        body.put("expires_at", resultSet.expiresAt());
        body.put("cache_hit_flag", resultSet.cacheHitFlag());
        body.put("stable_order_digest", resultSet.stableOrderDigest());
        body.put("rebuild_generation", resultSet.rebuildGeneration());
        return body;
    }

    private Map<String, Object> facets(List<SearchDocumentState> documents) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byOrg = new LinkedHashMap<>();
        for (SearchDocumentState document : documents) {
            byType.merge(document.docType(), 1, Integer::sum);
            byOrg.merge(ownerOrg(document), 1, Integer::sum);
        }
        return Map.of("doc_type", byType, "owner_org_unit_id", byOrg);
    }

    private void markOldDocumentVersionsStale(String documentAssetId, String activeDocumentVersionId) {
        for (SearchDocumentState document : new ArrayList<>(searchDocuments.values())) {
            if (documentAssetId != null && documentAssetId.equals(document.documentAssetId()) && !activeDocumentVersionId.equals(document.documentVersionId())) {
                SearchDocumentState stale = new SearchDocumentState(document.searchDocId(), document.docType(), document.sourceObjectId(), document.sourceAnchorJson(),
                        document.sourceVersionDigest(), document.contractId(), document.documentAssetId(), document.documentVersionId(), document.semanticRefId(), document.titleText(),
                        document.bodyText(), document.keywordText(), document.filterPayloadJson(), document.sortPayloadJson(), document.localeCode(), document.visibilityScopeJson(),
                        "STALE", document.rebuildGeneration(), document.indexedAt());
                searchDocuments.put(searchDocumentKey(stale.searchDocId(), stale.rebuildGeneration()), stale);
                persistSearchDocument(stale);
            }
        }
    }

    private String ownerOrg(SearchDocumentState document) {
        return text(mapFromJson(document.filterPayloadJson()), "owner_org_unit_id", null);
    }

    private String searchDocId(String docType, String sourceObjectId, String digest) {
        return "search-doc-" + Integer.toHexString((docType + ":" + sourceObjectId + ":" + digest).hashCode());
    }

    private String searchDocumentKey(String searchDocId, int rebuildGeneration) {
        return searchDocId + ":" + rebuildGeneration;
    }

    private String versionDigest(String docType, String sourceObjectId, String payload, String status) {
        return "sha256:" + Integer.toHexString((docType + ":" + sourceObjectId + ":" + payload + ":" + status).hashCode());
    }

    private String permissionDigest(String permissions, String orgScope) {
        String normalized = permissions == null ? "" : java.util.Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !"SEARCH_EXPORT".equals(value))
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "sha256:" + Integer.toHexString((normalized + ":" + (orgScope == null ? "" : orgScope)).hashCode());
    }

    private boolean hasSearchQueryPermission(String permissions) {
        return permissions != null && permissions.contains("SEARCH_QUERY") && permissions.contains("CONTRACT_VIEW");
    }

    private List<Map<String, Object>> exportItems(List<Map<String, Object>> items) {
        return items.stream().map(item -> {
            Map<String, Object> cropped = new LinkedHashMap<>(item);
            cropped.remove("body_snippet");
            cropped.remove("sensitive_body_text");
            return cropped;
        }).toList();
    }

    private void expireSearchResultSet(SearchResultSetState resultSet) {
        SearchResultSetState expired = new SearchResultSetState(resultSet.resultSetId(), "EXPIRED", resultSet.searchQueryJson(), resultSet.itemPayloadJson(), resultSet.facetPayloadJson(),
                resultSet.total(), resultSet.rankingProfileCode(), resultSet.expiresAt(), resultSet.cacheHitFlag(), resultSet.permissionScopeDigest(), resultSet.actorId(),
                resultSet.rebuildGeneration(), resultSet.stableOrderDigest(), resultSet.createdAt());
        searchResultSets.put(expired.resultSetId(), expired);
        jdbcTemplate.update("update ia_search_result_set set result_status = 'EXPIRED' where result_set_id = ?", expired.resultSetId());
    }

    private boolean isSearchSnapshotExpired(SearchResultSetState resultSet) {
        Timestamp expiresAt = jdbcTemplate.queryForObject("select expires_at from ia_search_result_set where result_set_id = ?", Timestamp.class, resultSet.resultSetId());
        Instant effectiveExpiresAt = expiresAt == null ? Instant.parse(resultSet.expiresAt()) : expiresAt.toInstant();
        return !Instant.now().isBefore(effectiveExpiresAt);
    }

    private SearchResultSetState requireSearchResultSet(String resultSetId) {
        SearchResultSetState resultSet = searchResultSets.get(resultSetId);
        if (resultSet == null) {
            throw new IllegalArgumentException("result_set_id 不存在: " + resultSetId);
        }
        return resultSet;
    }

    private void persistSearchSourceEnvelope(SearchSourceEnvelopeState envelope) {
        jdbcTemplate.update("""
                insert into ia_search_source_envelope
                (envelope_id, doc_type, source_object_id, source_version_digest, contract_id, document_asset_id, document_version_id, semantic_ref_id, source_anchor_json, title_text, body_text, keyword_text, owner_org_unit_id, locale_code, admission_status, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, envelope.envelopeId(), envelope.docType(), envelope.sourceObjectId(), envelope.sourceVersionDigest(), envelope.contractId(), envelope.documentAssetId(),
                envelope.documentVersionId(), envelope.semanticRefId(), envelope.sourceAnchorJson(), envelope.titleText(), envelope.bodyText(), envelope.keywordText(),
                envelope.ownerOrgUnitId(), envelope.localeCode(), envelope.admissionStatus(), Timestamp.from(Instant.now()));
    }

    private void persistSearchDocument(SearchDocumentState document) {
        jdbcTemplate.update("DELETE FROM ia_search_document WHERE search_doc_id = ? AND rebuild_generation = ?", document.searchDocId(), document.rebuildGeneration());
        jdbcTemplate.update("""
                insert into ia_search_document
                (search_doc_id, doc_type, source_object_id, source_anchor_json, source_version_digest, contract_id, document_asset_id, document_version_id, semantic_ref_id, title_text, body_text, keyword_text, filter_payload_json, sort_payload_json, locale_code, visibility_scope_json, exposure_status, rebuild_generation, indexed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, document.searchDocId(), document.docType(), document.sourceObjectId(), document.sourceAnchorJson(), document.sourceVersionDigest(), document.contractId(),
                document.documentAssetId(), document.documentVersionId(), document.semanticRefId(), document.titleText(), document.bodyText(), document.keywordText(),
                document.filterPayloadJson(), document.sortPayloadJson(), document.localeCode(), document.visibilityScopeJson(), document.exposureStatus(), document.rebuildGeneration(), Timestamp.from(Instant.now()));
    }

    private void persistSearchResultSet(SearchResultSetState resultSet) {
        jdbcTemplate.update("""
                insert into ia_search_result_set
                (result_set_id, search_query_json, result_status, item_payload_json, facet_payload_json, total, ranking_profile_code, expires_at, cache_hit_flag, permission_scope_digest, actor_id, rebuild_generation, stable_order_digest, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, resultSet.resultSetId(), resultSet.searchQueryJson(), resultSet.resultStatus(), resultSet.itemPayloadJson(), resultSet.facetPayloadJson(), resultSet.total(),
                resultSet.rankingProfileCode(), Timestamp.from(Instant.parse(resultSet.expiresAt())), resultSet.cacheHitFlag(), resultSet.permissionScopeDigest(), resultSet.actorId(),
                resultSet.rebuildGeneration(), resultSet.stableOrderDigest(), Timestamp.from(Instant.now()));
    }

    private void appendSearchAudit(String actionType, String actorId, String contractId, String resultSetId, String resultStatus, String traceId) {
        jdbcTemplate.update("""
                insert into ia_search_audit_event
                (audit_event_id, action_type, actor_id, contract_id, result_set_id, rebuild_generation, result_status, trace_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "search-audit-" + UUID.randomUUID(), actionType, actorId, contractId, resultSetId, activeSearchGeneration, resultStatus, traceId, Timestamp.from(Instant.now()));
    }

    private OcrJobState executeOcrJob(OcrJobState job, Map<String, Object> request) {
        int simulatedFailures = intValue(request, "simulate_engine_failure_count", 0);
        List<Map<String, Object>> retries = new ArrayList<>();
        for (int attempt = 1; attempt <= Math.min(simulatedFailures, job.maxAttemptNo()); attempt++) {
            Map<String, Object> retry = appendOcrRetry(job, attempt, "ENGINE_TEMPORARY_UNAVAILABLE", "OCR 引擎临时不可用", true);
            retries.add(retry);
            appendOcrAudit(job, null, "OCR_ENGINE_RETRY", "RETRYING", "OCR 引擎临时不可用");
        }
        int attemptNo = Math.min(simulatedFailures + 1, job.maxAttemptNo());
        if (simulatedFailures >= job.maxAttemptNo() || bool(request, "simulate_engine_permanent_failure", false)) {
            String resultId = "ocr-result-" + UUID.randomUUID();
            OcrResultState failed = failedOcrResult(job, resultId);
            ocrResults.put(resultId, failed);
            persistOcrResult(failed);
            OcrJobState updated = updateOcrJob(job, "FAILED", attemptNo, resultId, "ENGINE_FAILED", "OCR 引擎失败达到上限");
            appendOcrAudit(updated, failed, "OCR_RESULT_FAILED", "FAILED", "OCR 引擎失败达到上限");
            return updated;
        }

        OcrEngineAdapter adapter = new BaselineOcrEngineAdapter();
        OcrResultState result = adapter.recognize(job, requireContract(job.contractId()).contractName(), bool(request, "simulate_partial_result", false));
        ocrResults.put(result.ocrResultAggregateId(), result);
        currentOcrResultByDocumentVersion.put(result.documentVersionId(), result.ocrResultAggregateId());
        persistOcrResult(result);
        persistOcrResultParts(result);
        OcrJobState updated = updateOcrJob(job, "SUCCEEDED", attemptNo, result.ocrResultAggregateId(), null, null);
        appendOcrAudit(updated, result, "OCR_RESULT_" + result.resultStatus(), result.resultStatus(), null);
        appendDocumentConsumerEvent("OCR_RESULT_" + result.resultStatus(), requireDocumentAsset(result.documentAssetId()), result.documentVersionId(), job.traceId());
        appendDocumentConsumerEvent("IA_SEARCH_REINDEX_REQUESTED", requireDocumentAsset(result.documentAssetId()), result.documentVersionId(), job.traceId());
        createPlatformJob("IA_SEARCH_REINDEX", "intelligent-applications", "intelligent-applications", "OCR_RESULT", result.ocrResultAggregateId(), "CONTRACT", result.contractId(), job.traceId(), "ia-search-reindex-runner");
        return updated;
    }

    private OcrInput resolveOcrInput(Map<String, Object> request) {
        String documentVersionId = text(request, "document_version_id", null);
        String documentAssetId = text(request, "document_asset_id", null);
        if (documentVersionId == null && documentAssetId == null) {
            throw new IllegalArgumentException("document_asset_id 或 document_version_id 必须提供一个");
        }
        if (documentVersionId == null) {
            DocumentAssetState asset = requireDocumentAsset(documentAssetId);
            documentVersionId = asset.currentVersionId();
        }
        DocumentVersionState version = requireDocumentVersion(documentVersionId);
        DocumentAssetState asset = requireDocumentAsset(version.documentAssetId());
        if (documentAssetId != null && !documentAssetId.equals(asset.documentAssetId())) {
            throw new IllegalArgumentException("document_asset_id 与 document_version_id 不匹配");
        }
        if (!"ACTIVE".equals(documentVersionBody(version).get("version_status"))) {
            throw new IllegalArgumentException("仅允许基于当前受控版本创建 OCR 作业");
        }
        return new OcrInput(asset, version);
    }

    private OcrJobState updateOcrJob(OcrJobState job, String status, int attemptNo, String resultId, String failureCode, String failureReason) {
        OcrJobState updated = new OcrJobState(job.ocrJobId(), job.contractId(), job.documentAssetId(), job.documentVersionId(), job.inputContentFingerprint(),
                job.jobPurpose(), status, job.languageHintJson(), job.qualityProfileCode(), job.engineRouteCode(), attemptNo, job.maxAttemptNo(), resultId,
                failureCode, failureReason, job.idempotencyKey(), job.traceId(), job.actorId(), job.platformJobId(), job.createdAt(), Instant.now().toString());
        ocrJobs.put(updated.ocrJobId(), updated);
        jdbcTemplate.update("""
                update ia_ocr_job
                set job_status = ?, current_attempt_no = ?, result_aggregate_id = ?, failure_code = ?, failure_reason = ?, updated_at = ?
                where ocr_job_id = ?
                """, status, attemptNo, resultId, failureCode, failureReason, Timestamp.from(Instant.now()), job.ocrJobId());
        return updated;
    }

    private OcrResultState failedOcrResult(OcrJobState job, String resultId) {
        return new OcrResultState(resultId, job.ocrJobId(), job.contractId(), job.documentAssetId(), job.documentVersionId(), "FAILED", "ocr-result-v1",
                job.qualityProfileCode(), "ocr://text/" + resultId, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0.0,
                job.inputContentFingerprint(), null, false, Instant.now().toString(), Instant.now().toString());
    }

    private void supersedeOcrResultsForVersionSwitch(DocumentAssetState oldAsset, String newDocumentVersionId, String traceId) {
        List<OcrResultState> resultsToSupersede = ocrResults.values().stream()
                .filter(result -> oldAsset.documentAssetId().equals(result.documentAssetId()))
                .filter(OcrResultState::defaultConsumable)
                .filter(result -> "READY".equals(result.resultStatus()) || "PARTIAL".equals(result.resultStatus()))
                .toList();
        for (OcrResultState result : resultsToSupersede) {
            OcrResultState superseded = new OcrResultState(result.ocrResultAggregateId(), result.ocrJobId(), result.contractId(), result.documentAssetId(), result.documentVersionId(),
                    "SUPERSEDED", result.resultSchemaVersion(), result.qualityProfileCode(), result.fullTextRef(), result.textLayer(), result.layoutBlocks(),
                    result.tableRegions(), result.sealRegions(), result.fieldCandidates(), result.languageSegments(), result.pageSummary(), result.qualityScore(),
                    result.contentFingerprint(), null, false, result.createdAt(), Instant.now().toString());
            ocrResults.put(superseded.ocrResultAggregateId(), superseded);
            currentOcrResultByDocumentVersion.remove(superseded.documentVersionId());
            jdbcTemplate.update("""
                    update ia_ocr_result_aggregate
                    set result_status = 'SUPERSEDED', default_consumable = false, updated_at = ?
                    where ocr_result_aggregate_id = ?
                    """, Timestamp.from(Instant.now()), superseded.ocrResultAggregateId());
            appendOcrAudit(requireOcrJob(superseded.ocrJobId()), superseded, "OCR_RESULT_SUPERSEDED", "SUPERSEDED", null);
            appendDocumentConsumerEvent("OCR_RESULT_SUPERSEDED", oldAsset, superseded.documentVersionId(), traceId);
        }
        appendDocumentConsumerEvent("OCR_REBUILD_REQUESTED", oldAsset, newDocumentVersionId, traceId);
        createPlatformJob("IA_OCR_REBUILD", "document-center", "intelligent-applications", "DOCUMENT_VERSION", newDocumentVersionId, "CONTRACT", oldAsset.ownerId(), traceId, "ia-ocr-rebuild-runner");
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
        ContractState contract = requireContract(asset.ownerId());
        if (("TERMINATED".equals(contract.contractStatus()) || "ARCHIVED".equals(contract.contractStatus()))
                && !"ARCHIVE".equals(scene)) {
            Map<String, Object> audit = encryptionAudit("DECRYPT_ACCESS_DENIED", "REJECTED", binding.securityBindingId(), documentAssetId, documentVersionId,
                    asset.ownerId(), text(request, "access_subject_type", "USER"), text(request, "access_subject_id", null), text(request, "actor_department_id", null), null, traceId);
            asset.auditRecords().add(audit);
            Map<String, Object> body = error("CONTRACT_ARCHIVED_CONTROLLED_ACCESS_RESTRICTED", "合同终止或归档后仅允许归档场景受控访问");
            body.put("contract_status", contract.contractStatus());
            body.put("audit_event", audit);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
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
        ContractState contract = requireContract(asset.ownerId());
        if ("TERMINATED".equals(contract.contractStatus()) || "ARCHIVED".equals(contract.contractStatus())) {
            Map<String, Object> audit = encryptionAudit("DOWNLOAD_EXPORT_DENIED", "REJECTED", binding.securityBindingId(), documentAssetId, documentVersionId,
                    asset.ownerId(), "USER", text(request, "requested_by", null), text(request, "requested_department_id", null), null, text(request, "trace_id", null));
            asset.auditRecords().add(audit);
            Map<String, Object> body = error("CONTRACT_ARCHIVED_CONTROLLED_ACCESS_RESTRICTED", "合同终止或归档后不允许普通明文导出");
            body.put("contract_status", contract.contractStatus());
            body.put("audit_event", audit);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
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
        body.put("timeline_summary", combinedTimeline(contract));
        body.put("audit_record", combinedAudit(contract));
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
        persistPerformanceRecord(record);
        Map<String, Object> summary = refreshPerformanceSummary(contractId, "PERFORMANCE_STARTED");
        lifecycleSummaries.put(contractId, lifecycleSummary(contractId, summary));
        appendLifecycleTimeline(contractId, "PERFORMANCE_STARTED", "PERFORMANCE_RECORD", recordId, "PERFORMANCE_STARTED",
                text(request, "operator_user_id", record.ownerUserId()), "SUCCESS", null, text(request, "trace_id", null));
        appendLifecycleAudit(contractId, "PERFORMANCE_RECORD_CREATED", "PERFORMANCE_RECORD", recordId,
                text(request, "operator_user_id", record.ownerUserId()), "SUCCESS", "ADMITTED", null, text(request, "trace_id", null));
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
        persistPerformanceNode(node);
        persistPerformanceDocumentRef(documentRef);
        Map<String, Object> summary = refreshPerformanceSummary(contractId, node.milestoneCode());
        lifecycleSummaries.put(contractId, lifecycleSummary(contractId, summary));
        appendLifecycleTimeline(contractId, "PERFORMANCE_NODE_CREATED", "PERFORMANCE_NODE", nodeId, node.milestoneCode(),
                text(request, "operator_user_id", node.ownerUserId()), "SUCCESS", lifecycleDocumentRefId(documentRef), text(request, "trace_id", null));
        appendLifecycleAudit(contractId, "PERFORMANCE_NODE_CREATED", "PERFORMANCE_NODE", nodeId,
                text(request, "operator_user_id", node.ownerUserId()), "SUCCESS", "CREATED", lifecycleDocumentRefId(documentRef), text(request, "trace_id", null));
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
        int progressPercent = intValue(request, "progress_percent", node.progressPercent());
        int issueCount = intValue(request, "issue_count", node.issueCount());
        ResponseEntity<Map<String, Object>> stateRejection = validatePerformanceNodeChange(node, nodeStatus, progressPercent, riskLevel, issueCount, request);
        if (stateRejection != null) {
            return stateRejection;
        }
        boolean overdue = node.overdue() || bool(request, "is_overdue", "OVERDUE".equals(nodeStatus)) || "OVERDUE".equals(nodeStatus);
        PerformanceNodeState updated = new PerformanceNodeState(node.performanceNodeId(), node.performanceRecordId(), node.contractId(),
                node.nodeType(), node.nodeName(), node.milestoneCode(), node.plannedAt(), node.dueAt(), text(request, "actual_at", node.actualAt()),
                nodeStatus, progressPercent, riskLevel, issueCount,
                overdue, text(request, "result_summary", node.resultSummary()), Instant.now().toString(), node.ownerUserId(), node.ownerOrgUnitId(), node.documentRef());
        performanceNodes.put(nodeId, updated);
        persistPerformanceNode(updated);

        String milestoneCode = "COMPLETED".equals(nodeStatus) ? "PERFORMANCE_COMPLETED" : updated.milestoneCode();
        Map<String, Object> summary = refreshPerformanceSummary(contractId, milestoneCode);
        lifecycleSummaries.put(contractId, lifecycleSummary(contractId, summary));
        String actorUserId = text(request, "operator_user_id", updated.ownerUserId());
        String documentRefId = lifecycleDocumentRefId(updated.documentRef());
        if (!previousRisk.equals(riskLevel)) {
            appendLifecycleAudit(contractId, "PERFORMANCE_RISK_CHANGED", "PERFORMANCE_NODE", nodeId,
                    actorUserId, "SUCCESS", riskLevel, documentRefId, text(request, "trace_id", null));
            appendContractEvent(contractId, "PERFORMANCE_RISK_CHANGED", nodeId, text(request, "trace_id", null), null);
        }
        if (overdue || "OVERDUE".equals(nodeStatus)) {
            appendLifecycleTimeline(contractId, "PERFORMANCE_NODE_OVERDUE", "PERFORMANCE_NODE", nodeId, updated.milestoneCode(),
                    actorUserId, "OVERDUE", documentRefId, text(request, "trace_id", null));
            appendContractEvent(contractId, "PERFORMANCE_NODE_OVERDUE", nodeId, text(request, "trace_id", null), null);
        }
        if ("COMPLETED".equals(summary.get("performance_status"))) {
            appendLifecycleTimeline(contractId, "PERFORMANCE_COMPLETED", "PERFORMANCE_NODE", nodeId, "PERFORMANCE_COMPLETED",
                    actorUserId, "COMPLETED", documentRefId, text(request, "trace_id", null));
            appendLifecycleAudit(contractId, "PERFORMANCE_COMPLETION_CONFIRMED", "PERFORMANCE_NODE", nodeId,
                    actorUserId, "SUCCESS", "COMPLETED", documentRefId, text(request, "trace_id", null));
            appendContractEvent(contractId, "PERFORMANCE_COMPLETED", nodeId, text(request, "trace_id", null), "PERFORMED");
        } else {
            appendLifecycleTimeline(contractId, "PERFORMANCE_PROGRESS_UPDATED", "PERFORMANCE_NODE", nodeId, updated.milestoneCode(),
                    actorUserId, summary.get("performance_status").toString(), documentRefId, text(request, "trace_id", null));
            appendContractEvent(contractId, "PERFORMANCE_PROGRESS_UPDATED", nodeId, text(request, "trace_id", null), null);
        }

        Map<String, Object> body = performanceNodeBody(updated);
        body.put("performance_summary", summary);
        return ResponseEntity.ok(body);
    }

    ResponseEntity<Map<String, Object>> createContractChange(String contractId, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        if ("TERMINATED".equals(contract.contractStatus()) || "ARCHIVED".equals(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONTRACT_TERMINATED_CHANGE_RESTRICTED", "合同终止或归档后不允许继续发起变更"));
        }
        if (!List.of("SIGNED", "PERFORMED", "CHANGED").contains(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONTRACT_NOT_EFFECTIVE_FOR_CHANGE", "合同未生效，不能发起变更"));
        }
        String changeId = "cl-change-" + UUID.randomUUID();
        Map<String, Object> documentRef = lifecycleDocumentRef(contractId, "CONTRACT_CHANGE", changeId, "CHANGE_AGREEMENT",
                text(request, "supplemental_document_asset_id", null), text(request, "supplemental_document_version_id", null));
        String workflowInstanceId = text(request, "workflow_instance_id", "wf-change-" + UUID.randomUUID());
        Map<String, Object> processRef = lifecycleProcessRef(contractId, "CONTRACT_CHANGE", changeId, workflowInstanceId, "CONTRACT_CHANGE_APPROVAL", "APPROVING");
        ContractChangeState change = new ContractChangeState(changeId, contractId, text(request, "change_type", "GENERAL"), text(request, "change_reason", null),
                text(request, "change_summary", null), map(request.get("impact_scope")), text(request, "effective_date", null), "APPROVING",
                workflowInstanceId, documentRef, processRef, null, null, null, 1);
        contractChanges.put(changeId, change);
        persistChange(change);
        persistPerformanceDocumentRef(documentRef);
        persistLifecycleProcessRef(processRef);
        appendLifecycleTimeline(contractId, "CHANGE_APPROVAL_STARTED", "CONTRACT_CHANGE", changeId, "CHANGE_APPROVING",
                text(request, "operator_user_id", null), "APPROVING", lifecycleDocumentRefId(documentRef), text(request, "trace_id", null));
        appendLifecycleAudit(contractId, "CHANGE_APPLIED_FOR_APPROVAL", "CONTRACT_CHANGE", changeId,
                text(request, "operator_user_id", null), "SUCCESS", "APPROVING", lifecycleDocumentRefId(documentRef), text(request, "trace_id", null));
        return ResponseEntity.status(HttpStatus.CREATED).body(changeBody(change));
    }

    ResponseEntity<Map<String, Object>> applyContractChangeResult(String contractId, String changeId, Map<String, Object> request) {
        ContractChangeState change = requireChange(changeId);
        if (!contractId.equals(change.contractId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CHANGE_CONTRACT_MISMATCH", "变更记录未绑定当前合同"));
        }
        String result = text(request, "approval_result", "APPROVED");
        boolean approved = "APPROVED".equals(result);
        String nextStatus = approved ? "APPLIED" : "REJECTED";
        Map<String, Object> processRef = lifecycleProcessRef(change.processRef(), text(request, "workflow_instance_id", change.workflowInstanceId()), nextStatus);
        ContractChangeState updated = new ContractChangeState(change.changeId(), change.contractId(), change.changeType(), change.changeReason(),
                change.changeSummary(), change.impactScope(), change.effectiveDate(), nextStatus, text(request, "workflow_instance_id", change.workflowInstanceId()),
                change.documentRef(), processRef, approved ? text(request, "approved_at", Instant.now().toString()) : null,
                approved ? Instant.now().toString() : null, approved ? text(request, "result_summary", change.changeSummary()) : null, change.resultVersionNo() + 1);
        contractChanges.put(changeId, updated);
        persistChange(updated);
        persistLifecycleProcessRef(processRef);
        if (approved) {
            Map<String, Object> summary = changeSummary(updated);
            changeSummaries.put(contractId, summary);
            refreshLifecycleSummary(contractId, "CHANGE", nextStatus, "CHANGE_APPROVED");
            appendContractEvent(contractId, "CHANGE_APPLIED", changeId, text(request, "trace_id", null), "CHANGED");
            appendLifecycleTimeline(contractId, "CHANGE_APPLIED", "CONTRACT_CHANGE", changeId, "CHANGE_APPROVED",
                    text(request, "operator_user_id", null), "APPLIED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
            appendLifecycleAudit(contractId, "CHANGE_APPLIED", "CONTRACT_CHANGE", changeId,
                    text(request, "operator_user_id", null), "SUCCESS", "APPLIED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
        } else {
            refreshLifecycleSummary(contractId, "CHANGE", nextStatus, "CHANGE_REJECTED");
            appendLifecycleTimeline(contractId, "CHANGE_REJECTED", "CONTRACT_CHANGE", changeId, "CHANGE_REJECTED",
                    text(request, "operator_user_id", null), "REJECTED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
            appendLifecycleAudit(contractId, "CHANGE_REJECTED", "CONTRACT_CHANGE", changeId,
                    text(request, "operator_user_id", null), "REJECTED", "REJECTED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
        }
        return ResponseEntity.ok(changeBody(updated));
    }

    ResponseEntity<Map<String, Object>> createContractTermination(String contractId, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        if (!List.of("SIGNED", "PERFORMED", "CHANGED").contains(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONTRACT_NOT_EFFECTIVE_FOR_TERMINATION", "合同状态不允许发起终止"));
        }
        String terminationId = "cl-term-" + UUID.randomUUID();
        Map<String, Object> documentRef = lifecycleDocumentRef(contractId, "CONTRACT_TERMINATION", terminationId, "TERMINATION_AGREEMENT",
                text(request, "material_document_asset_id", null), text(request, "material_document_version_id", null));
        String workflowInstanceId = text(request, "workflow_instance_id", "wf-term-" + UUID.randomUUID());
        Map<String, Object> processRef = lifecycleProcessRef(contractId, "CONTRACT_TERMINATION", terminationId, workflowInstanceId, "CONTRACT_TERMINATION_APPROVAL", "APPROVING");
        ContractTerminationState termination = new ContractTerminationState(terminationId, contractId, text(request, "termination_type", "GENERAL"),
                text(request, "termination_reason", null), text(request, "termination_summary", null), text(request, "requested_termination_date", null),
                text(request, "settlement_summary", null), "APPROVING", workflowInstanceId, documentRef, processRef, null,
                text(request, "post_action_status", "PENDING"), text(request, "access_restriction", "PENDING_TERMINATION_APPROVAL"));
        contractTerminations.put(terminationId, termination);
        persistTermination(termination);
        persistPerformanceDocumentRef(documentRef);
        persistLifecycleProcessRef(processRef);
        appendLifecycleTimeline(contractId, "TERMINATION_APPROVAL_STARTED", "CONTRACT_TERMINATION", terminationId, "TERMINATION_APPROVING",
                text(request, "operator_user_id", null), "APPROVING", lifecycleDocumentRefId(documentRef), text(request, "trace_id", null));
        appendLifecycleAudit(contractId, "TERMINATION_APPLIED_FOR_APPROVAL", "CONTRACT_TERMINATION", terminationId,
                text(request, "operator_user_id", null), "SUCCESS", "APPROVING", lifecycleDocumentRefId(documentRef), text(request, "trace_id", null));
        return ResponseEntity.status(HttpStatus.CREATED).body(terminationBody(termination));
    }

    ResponseEntity<Map<String, Object>> applyContractTerminationResult(String contractId, String terminationId, Map<String, Object> request) {
        ContractTerminationState termination = requireTermination(terminationId);
        if (!contractId.equals(termination.contractId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("TERMINATION_CONTRACT_MISMATCH", "终止记录未绑定当前合同"));
        }
        String result = text(request, "approval_result", "APPROVED");
        boolean approved = "APPROVED".equals(result);
        String nextStatus = approved ? "TERMINATED" : "REJECTED";
        Map<String, Object> processRef = lifecycleProcessRef(termination.processRef(), text(request, "workflow_instance_id", termination.workflowInstanceId()), nextStatus);
        ContractTerminationState updated = new ContractTerminationState(termination.terminationId(), termination.contractId(), termination.terminationType(),
                termination.terminationReason(), termination.terminationSummary(), termination.requestedTerminationDate(),
                text(request, "settlement_summary", termination.settlementSummary()), nextStatus,
                text(request, "workflow_instance_id", termination.workflowInstanceId()), termination.documentRef(),
                processRef, approved ? text(request, "terminated_at", Instant.now().toString()) : null,
                text(request, "post_action_status", termination.postActionStatus()), text(request, "access_restriction", "READ_ONLY_AFTER_TERMINATION"));
        contractTerminations.put(terminationId, updated);
        persistTermination(updated);
        persistLifecycleProcessRef(processRef);
        if (approved) {
            Map<String, Object> summary = terminationSummary(updated);
            terminationSummaries.put(contractId, summary);
            refreshLifecycleSummary(contractId, "TERMINATION", nextStatus, "TERMINATION_COMPLETED");
            appendContractEvent(contractId, "TERMINATION_COMPLETED", terminationId, text(request, "trace_id", null), "TERMINATED");
            appendLifecycleTimeline(contractId, "TERMINATION_COMPLETED", "CONTRACT_TERMINATION", terminationId, "TERMINATION_COMPLETED",
                    text(request, "operator_user_id", null), "TERMINATED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
            appendLifecycleAudit(contractId, "TERMINATION_COMPLETED", "CONTRACT_TERMINATION", terminationId,
                    text(request, "operator_user_id", null), "SUCCESS", "TERMINATED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
        } else {
            refreshLifecycleSummary(contractId, "TERMINATION", nextStatus, "TERMINATION_REJECTED");
            appendLifecycleTimeline(contractId, "TERMINATION_REJECTED", "CONTRACT_TERMINATION", terminationId, "TERMINATION_REJECTED",
                    text(request, "operator_user_id", null), "REJECTED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
            appendLifecycleAudit(contractId, "TERMINATION_REJECTED", "CONTRACT_TERMINATION", terminationId,
                    text(request, "operator_user_id", null), "REJECTED", "REJECTED", lifecycleDocumentRefId(updated.documentRef()), text(request, "trace_id", null));
        }
        return ResponseEntity.ok(terminationBody(updated));
    }

    ResponseEntity<Map<String, Object>> createArchiveRecord(String contractId, Map<String, Object> request) {
        ContractState contract = requireContract(contractId);
        if (!List.of("PERFORMED", "CHANGED", "TERMINATED").contains(contract.contractStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("CONTRACT_NOT_READY_FOR_ARCHIVE", "合同未满足归档准入条件"));
        }
        List<String> inputSet = listStrings(request.get("input_set"));
        ResponseEntity<Map<String, Object>> inputRejection = validateArchiveInputs(contract, inputSet, request);
        if (inputRejection != null) {
            return inputRejection;
        }
        String archiveId = "cl-archive-" + UUID.randomUUID();
        Map<String, Object> packageRef = lifecycleDocumentRef(contractId, "ARCHIVE_RECORD", archiveId, "ARCHIVE_PACKAGE",
                text(request, "package_document_asset_id", null), text(request, "package_document_version_id", null));
        Map<String, Object> manifestRef = lifecycleDocumentRef(contractId, "ARCHIVE_RECORD", archiveId, "ARCHIVE_MANIFEST",
                text(request, "manifest_document_asset_id", null), text(request, "manifest_document_version_id", null));
        ArchiveRecordState archive = new ArchiveRecordState(archiveId, contractId, text(request, "archive_batch_no", "ARCH-" + UUID.randomUUID()),
                text(request, "archive_type", "FINAL"), text(request, "archive_reason", null), inputSet, "ARCHIVED", "PASSED",
                text(request, "archive_keeper_user_id", null), text(request, "archive_location_code", null), packageRef, manifestRef, Instant.now().toString());
        archiveRecords.put(archiveId, archive);
        persistArchive(archive);
        persistPerformanceDocumentRef(packageRef);
        persistPerformanceDocumentRef(manifestRef);
        Map<String, Object> summary = archiveSummary(archive, null);
        archiveSummaries.put(contractId, summary);
        refreshLifecycleSummary(contractId, "ARCHIVE", "ARCHIVED", "ARCHIVE_COMPLETED");
        appendContractEvent(contractId, "ARCHIVE_COMPLETED", archiveId, text(request, "trace_id", null), "ARCHIVED");
        appendLifecycleTimeline(contractId, "ARCHIVE_COMPLETED", "ARCHIVE_RECORD", archiveId, "ARCHIVE_COMPLETED",
                text(request, "operator_user_id", null), "ARCHIVED", lifecycleDocumentRefId(packageRef), text(request, "trace_id", null));
        appendLifecycleAudit(contractId, "ARCHIVE_COMPLETED", "ARCHIVE_RECORD", archiveId,
                text(request, "operator_user_id", null), "SUCCESS", "ARCHIVED", lifecycleDocumentRefId(packageRef), text(request, "trace_id", null));
        return ResponseEntity.status(HttpStatus.CREATED).body(archiveBody(archive));
    }

    ResponseEntity<Map<String, Object>> borrowArchive(String contractId, String archiveRecordId, Map<String, Object> request) {
        ArchiveRecordState archive = requireArchive(archiveRecordId);
        if (!contractId.equals(archive.contractId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("ARCHIVE_CONTRACT_MISMATCH", "归档记录未绑定当前合同"));
        }
        String borrowId = "cl-borrow-" + UUID.randomUUID();
        ArchiveBorrowState borrow = new ArchiveBorrowState(borrowId, contractId, archiveRecordId, archive.archiveBatchNo(), archive.packageRef(), archive.manifestRef(),
                "BORROWED", text(request, "borrow_purpose", null), text(request, "requested_by", null), text(request, "requested_org_unit_id", null),
                Instant.now().toString(), text(request, "due_at", null), null);
        archiveBorrows.put(borrowId, borrow);
        persistBorrow(borrow);
        archiveSummaries.put(contractId, archiveSummary(archive, borrow));
        refreshLifecycleSummary(contractId, "ARCHIVE", "ARCHIVED", "ARCHIVE_BORROWED");
        appendLifecycleAudit(contractId, "ARCHIVE_BORROWED", "ARCHIVE_BORROW", borrowId,
                borrow.requestedBy(), "SUCCESS", "BORROWED", lifecycleDocumentRefId(archive.packageRef()), text(request, "trace_id", null));
        return ResponseEntity.status(HttpStatus.CREATED).body(borrowBody(borrow));
    }

    ResponseEntity<Map<String, Object>> returnArchiveBorrow(String contractId, String borrowRecordId, Map<String, Object> request) {
        ArchiveBorrowState borrow = requireBorrow(borrowRecordId);
        if (!contractId.equals(borrow.contractId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("BORROW_CONTRACT_MISMATCH", "借阅记录未绑定当前合同"));
        }
        if (!"BORROWED".equals(borrow.borrowStatus())) {
            Map<String, Object> body = error("ARCHIVE_BORROW_STATUS_CONFLICT", "只有 BORROWED 状态允许归还");
            body.put("current_borrow_status", borrow.borrowStatus());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        ArchiveBorrowState returned = new ArchiveBorrowState(borrow.borrowRecordId(), borrow.contractId(), borrow.archiveRecordId(), borrow.archiveBatchNo(),
                borrow.packageRef(), borrow.manifestRef(), "RETURNED", borrow.borrowPurpose(), borrow.requestedBy(), borrow.requestedOrgUnitId(),
                borrow.requestedAt(), borrow.dueAt(), Instant.now().toString());
        archiveBorrows.put(borrowRecordId, returned);
        persistBorrow(returned);
        ArchiveRecordState archive = requireArchive(returned.archiveRecordId());
        archiveSummaries.put(contractId, archiveSummary(archive, returned));
        refreshLifecycleSummary(contractId, "ARCHIVE", "ARCHIVED", "ARCHIVE_RETURNED");
        appendLifecycleAudit(contractId, "ARCHIVE_BORROW_RETURNED", "ARCHIVE_BORROW", borrowRecordId,
                text(request, "returned_by", returned.requestedBy()), "SUCCESS", "RETURNED", lifecycleDocumentRefId(returned.packageRef()), text(request, "trace_id", null));
        return ResponseEntity.ok(returnBody(returned));
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
        body.put("task_center_ref", taskCenterRef("task-center-" + node.performanceNodeId(), "COMPLETED".equals(node.nodeStatus()) ? "COMPLETED" : "PUBLISHED"));
        return body;
    }

    private Map<String, Object> taskCenterRef(String taskCenterTaskId, String status) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("task_center_task_id", taskCenterTaskId);
        ref.put("task_center_status", status);
        return ref;
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
        int issueCount = nodes.stream().mapToInt(PerformanceNodeState::issueCount).sum();
        boolean completionReady = nodeCount > 0 && completedCount == nodeCount && overdueCount == 0
                && issueCount == 0 && "LOW".equals(riskLevel) && progress == 100;
        String status = completionReady ? "COMPLETED" : (overdueCount > 0 || "HIGH".equals(riskLevel) || issueCount > 0 ? "AT_RISK" : "IN_PROGRESS");
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
            persistPerformanceRecord(updated);
        }

        Map<String, Object> riskSummary = new LinkedHashMap<>();
        riskSummary.put("risk_level", riskLevel);
        riskSummary.put("overdue_node_count", overdueCount);
        riskSummary.put("issue_count", issueCount);

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
        persistLifecycleSummary(contractId, summary);
        return summary;
    }

    private Map<String, Object> lifecycleSummary(String contractId, Map<String, Object> performanceSummary) {
        return lifecycleSummary(contractId, "PERFORMANCE", text(performanceSummary, "performance_status", null), text(performanceSummary, "latest_milestone_code", null));
    }

    private Map<String, Object> refreshLifecycleSummary(String contractId, String currentStage, String stageStatus, String milestoneCode) {
        Map<String, Object> summary = lifecycleSummary(contractId, currentStage, stageStatus, milestoneCode);
        lifecycleSummaries.put(contractId, summary);
        persistLifecycleSummary(contractId, summary);
        return summary;
    }

    private Map<String, Object> lifecycleSummary(String contractId, String currentStage, String stageStatus, String milestoneCode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contract_id", contractId);
        summary.put("current_stage", currentStage);
        summary.put("stage_status", stageStatus);
        summary.put("performance_summary", performanceSummaries.get(contractId));
        summary.put("change_summary", changeSummaries.get(contractId));
        summary.put("termination_summary", terminationSummaries.get(contractId));
        summary.put("archive_summary", archiveSummaries.get(contractId));
        Map<String, Object> performanceSummary = performanceSummaries.get(contractId);
        summary.put("risk_summary", performanceSummary == null ? null : performanceSummary.get("risk_summary"));
        summary.put("latest_milestone_code", milestoneCode);
        summary.put("latest_milestone_at", Instant.now().toString());
        summary.put("summary_version", "cl-summary-v1");
        return summary;
    }

    private Map<String, Object> changeSummary(ContractChangeState change) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("latest_change_id", change.changeId());
        summary.put("change_status", change.changeStatus());
        summary.put("change_type", change.changeType());
        summary.put("effective_date", change.effectiveDate());
        summary.put("current_effective_summary", change.changeResultSummary());
        summary.put("impact_scope", change.impactScope());
        summary.put("workflow_instance_id", change.workflowInstanceId());
        summary.put("document_ref", change.documentRef());
        return summary;
    }

    private Map<String, Object> terminationSummary(ContractTerminationState termination) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("latest_termination_id", termination.terminationId());
        summary.put("termination_status", termination.terminationStatus());
        summary.put("terminated_at", termination.terminatedAt());
        summary.put("settlement_summary", termination.settlementSummary());
        summary.put("post_action_status", termination.postActionStatus());
        summary.put("access_restriction", termination.accessRestriction());
        summary.put("workflow_instance_id", termination.workflowInstanceId());
        summary.put("document_ref", termination.documentRef());
        return summary;
    }

    private Map<String, Object> archiveSummary(ArchiveRecordState archive, ArchiveBorrowState borrow) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("latest_archive_record_id", archive.archiveRecordId());
        summary.put("archive_status", archive.archiveStatus());
        summary.put("archive_batch_no", archive.archiveBatchNo());
        summary.put("archive_integrity_status", archive.archiveIntegrityStatus());
        summary.put("package_ref", archive.packageRef());
        summary.put("manifest_ref", archive.manifestRef());
        if (borrow != null) {
            summary.put("latest_borrow_record_id", borrow.borrowRecordId());
            summary.put("borrow_status", borrow.borrowStatus());
        }
        return summary;
    }

    private Map<String, Object> lifecycleDocumentRef(String contractId, String sourceResourceType, String sourceResourceId,
                                                     String documentRole, String documentAssetId, String documentVersionId) {
        DocumentAssetState asset = requireDocumentAsset(documentAssetId);
        DocumentVersionState version = requireDocumentVersion(documentVersionId);
        if (!contractId.equals(asset.ownerId()) || !asset.documentAssetId().equals(version.documentAssetId())) {
            throw new IllegalArgumentException("生命周期文档引用必须绑定当前合同的文档中心版本");
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("lifecycle_document_ref_id", "cl-doc-ref-" + UUID.randomUUID());
        ref.put("contract_id", contractId);
        ref.put("source_resource_type", sourceResourceType);
        ref.put("source_resource_id", sourceResourceId);
        ref.put("document_role", documentRole);
        ref.put("document_asset_id", documentAssetId);
        ref.put("document_version_id", documentVersionId);
        ref.put("is_primary", true);
        return ref;
    }

    private Map<String, Object> lifecycleProcessRef(String contractId, String sourceResourceType, String sourceResourceId,
                                                    String workflowInstanceId, String processPurpose, String status) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("lifecycle_process_ref_id", "cl-proc-ref-" + UUID.randomUUID());
        ref.put("contract_id", contractId);
        ref.put("source_resource_type", sourceResourceType);
        ref.put("source_resource_id", sourceResourceId);
        ref.put("workflow_instance_id", workflowInstanceId);
        ref.put("process_purpose", processPurpose);
        ref.put("process_status_snapshot", status);
        ref.put("task_center_ref", taskCenterRef("task-center-" + workflowInstanceId, "PUBLISHED"));
        return ref;
    }

    private Map<String, Object> lifecycleProcessRef(Map<String, Object> currentRef, String workflowInstanceId, String status) {
        Map<String, Object> ref = new LinkedHashMap<>(currentRef);
        ref.put("workflow_instance_id", workflowInstanceId);
        ref.put("process_status_snapshot", status);
        return ref;
    }

    private ResponseEntity<Map<String, Object>> validateArchiveInputs(ContractState contract, List<String> inputSet, Map<String, Object> request) {
        List<String> required = new ArrayList<>(List.of("CONTRACT_MASTER", "MAIN_BODY", "PERFORMANCE_SUMMARY"));
        if ("TERMINATED".equals(contract.contractStatus())) {
            required.add("TERMINATION_SUMMARY");
        }
        boolean missingRequired = !inputSet.containsAll(required)
                || !hasContractDocumentRole(contract.contractId(), "MAIN_BODY")
                || !performanceSummaries.containsKey(contract.contractId())
                || (required.contains("TERMINATION_SUMMARY") && !terminationSummaries.containsKey(contract.contractId()));
        if (missingRequired) {
            Map<String, Object> body = error("ARCHIVE_INPUT_SET_INCOMPLETE", "归档输入集缺少必需项或对应事实不存在");
            body.put("required_input_set", required);
            body.put("actual_input_set", inputSet);
            return ResponseEntity.unprocessableEntity().body(body);
        }
        if (!documentRefMatchesRole(contract.contractId(), text(request, "package_document_asset_id", null), text(request, "package_document_version_id", null), "ARCHIVE_PACKAGE")
                || !documentRefMatchesRole(contract.contractId(), text(request, "manifest_document_asset_id", null), text(request, "manifest_document_version_id", null), "ARCHIVE_MANIFEST")) {
            return ResponseEntity.unprocessableEntity().body(error("ARCHIVE_DOCUMENT_ROLE_MISMATCH", "归档封包和清单文档必须存在且角色匹配"));
        }
        return null;
    }

    private boolean hasContractDocumentRole(String contractId, String documentRole) {
        return documentAssets.values().stream()
                .anyMatch(asset -> contractId.equals(asset.ownerId()) && documentRole.equals(asset.documentRole()));
    }

    private boolean documentRefMatchesRole(String contractId, String documentAssetId, String documentVersionId, String documentRole) {
        DocumentAssetState asset = documentAssets.get(documentAssetId);
        DocumentVersionState version = documentVersions.get(documentVersionId);
        return asset != null && version != null
                && contractId.equals(asset.ownerId())
                && asset.documentAssetId().equals(version.documentAssetId())
                && documentRole.equals(asset.documentRole());
    }

    private Map<String, Object> changeBody(ContractChangeState change) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("change_id", change.changeId());
        body.put("contract_id", change.contractId());
        body.put("change_type", change.changeType());
        body.put("change_status", change.changeStatus());
        body.put("effective_date", change.effectiveDate());
        body.put("impact_scope", change.impactScope());
        body.put("process_ref", change.processRef());
        body.put("document_ref", change.documentRef());
        body.put("change_summary", changeSummary(change));
        return body;
    }

    private Map<String, Object> terminationBody(ContractTerminationState termination) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("termination_id", termination.terminationId());
        body.put("contract_id", termination.contractId());
        body.put("termination_type", termination.terminationType());
        body.put("termination_status", termination.terminationStatus());
        body.put("process_ref", termination.processRef());
        body.put("document_ref", termination.documentRef());
        body.put("termination_summary", terminationSummary(termination));
        return body;
    }

    private Map<String, Object> archiveBody(ArchiveRecordState archive) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("archive_record_id", archive.archiveRecordId());
        body.put("contract_id", archive.contractId());
        body.put("archive_batch_no", archive.archiveBatchNo());
        body.put("archive_status", archive.archiveStatus());
        body.put("archive_integrity_status", archive.archiveIntegrityStatus());
        body.put("package_ref", archive.packageRef());
        body.put("manifest_ref", archive.manifestRef());
        body.put("archive_summary", archiveSummary(archive, null));
        return body;
    }

    private Map<String, Object> borrowBody(ArchiveBorrowState borrow) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("borrow_record_id", borrow.borrowRecordId());
        body.put("contract_id", borrow.contractId());
        body.put("archive_record_id", borrow.archiveRecordId());
        body.put("borrow_status", borrow.borrowStatus());
        body.put("borrow_purpose", borrow.borrowPurpose());
        body.put("requested_by", borrow.requestedBy());
        body.put("due_at", borrow.dueAt());
        return body;
    }

    private Map<String, Object> returnBody(ArchiveBorrowState borrow) {
        Map<String, Object> body = borrowBody(borrow);
        body.put("return_status", borrow.borrowStatus());
        body.put("returned_at", borrow.returnedAt());
        return body;
    }

    private ResponseEntity<Map<String, Object>> validatePerformanceNodeChange(PerformanceNodeState current, String nextStatus,
                                                                               int progressPercent, String riskLevel, int issueCount,
                                                                               Map<String, Object> request) {
        if (!List.of("PENDING", "IN_PROGRESS", "OVERDUE", "COMPLETED").contains(nextStatus)) {
            return ResponseEntity.unprocessableEntity().body(error("PERFORMANCE_NODE_STATUS_INVALID", "履约节点状态不在允许范围内"));
        }
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(riskLevel)) {
            return ResponseEntity.unprocessableEntity().body(error("PERFORMANCE_RISK_LEVEL_INVALID", "履约风险等级不在允许范围内"));
        }
        if (progressPercent < 0 || progressPercent > 100) {
            return ResponseEntity.unprocessableEntity().body(error("PERFORMANCE_PROGRESS_INVALID", "履约进度必须在 0 到 100 之间"));
        }
        if (issueCount < 0) {
            return ResponseEntity.unprocessableEntity().body(error("PERFORMANCE_ISSUE_COUNT_INVALID", "履约问题数量不能为负数"));
        }
        if ("COMPLETED".equals(current.nodeStatus()) && !"COMPLETED".equals(nextStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PERFORMANCE_NODE_TRANSITION_INVALID", "已完成履约节点不允许回退"));
        }
        boolean legalTransition = current.nodeStatus().equals(nextStatus)
                || switch (current.nodeStatus()) {
                    case "PENDING" -> List.of("IN_PROGRESS", "OVERDUE", "COMPLETED").contains(nextStatus);
                    case "IN_PROGRESS" -> List.of("OVERDUE", "COMPLETED").contains(nextStatus);
                    case "OVERDUE" -> List.of("IN_PROGRESS", "COMPLETED").contains(nextStatus);
                    default -> false;
                };
        if (!legalTransition) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PERFORMANCE_NODE_TRANSITION_INVALID", "履约节点状态迁移不合法"));
        }
        if ("COMPLETED".equals(nextStatus)) {
            String actualAt = text(request, "actual_at", current.actualAt());
            boolean overdueFact = current.overdue() || "OVERDUE".equals(current.nodeStatus()) || bool(request, "is_overdue", false);
            boolean completionBlocked = progressPercent != 100 || !"LOW".equals(riskLevel) || issueCount != 0
                    || overdueFact || actualAt == null || actualAt.isBlank();
            if (completionBlocked) {
                Map<String, Object> body = error("PERFORMANCE_COMPLETION_BLOCKED", "履约完成必须满足进度、风险、问题、逾期和实际完成时间校验");
                body.put("required_progress_percent", 100);
                body.put("required_risk_level", "LOW");
                body.put("required_issue_count", 0);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
            }
        }
        return null;
    }

    private List<Map<String, Object>> lifecycleTimeline(String contractId) {
        return lifecycleTimelineEvents.stream()
                .filter(event -> contractId.equals(text(event, "contract_id", null)))
                .toList();
    }

    private List<Map<String, Object>> combinedTimeline(ContractState contract) {
        List<Map<String, Object>> events = nonLifecycleContractEvents(contract.events());
        events.addAll(lifecycleTimeline(contract.contractId()));
        return events;
    }

    private List<Map<String, Object>> lifecycleAudit(String contractId) {
        return lifecycleAuditEvents.stream()
                .filter(event -> contractId.equals(text(event, "contract_id", null)))
                .toList();
    }

    private List<Map<String, Object>> combinedAudit(ContractState contract) {
        List<Map<String, Object>> events = nonLifecycleContractEvents(contract.events());
        events.addAll(encryptedDocumentAuditRecords.stream()
                .filter(event -> contract.contractId().equals(text(event, "contract_id", null)))
                .map(LinkedHashMap::new)
                .map(event -> (Map<String, Object>) event)
                .toList());
        events.addAll(lifecycleAudit(contract.contractId()));
        return events;
    }

    private List<Map<String, Object>> nonLifecycleContractEvents(List<Map<String, Object>> events) {
        return new ArrayList<>(events.stream()
                .filter(event -> {
                    String type = text(event, "event_type", "");
                    return !type.startsWith("PERFORMANCE_") && !type.startsWith("CHANGE_")
                            && !type.startsWith("TERMINATION_") && !type.startsWith("ARCHIVE_");
                })
                .map(LinkedHashMap::new)
                .map(event -> (Map<String, Object>) event)
                .toList());
    }

    private void persistPerformanceRecord(PerformanceRecordState record) {
        jdbcTemplate.update("DELETE FROM cl_performance_record WHERE performance_record_id = ?", record.performanceRecordId());
        jdbcTemplate.update("""
                INSERT INTO cl_performance_record (performance_record_id, contract_id, performance_status, progress_percent, risk_level, owner_user_id, owner_org_unit_id, open_node_count, overdue_node_count, latest_due_at, summary_text, latest_milestone_code, last_evaluated_at, last_writeback_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, record.performanceRecordId(), record.contractId(), record.performanceStatus(), record.progressPercent(), record.riskLevel(),
                record.ownerUserId(), record.ownerOrgUnitId(), record.openNodeCount(), record.overdueNodeCount(), record.latestDueAt(),
                record.summaryText(), record.latestMilestoneCode(), record.lastEvaluatedAt(), record.lastWritebackAt());
    }

    private void persistPerformanceNode(PerformanceNodeState node) {
        jdbcTemplate.update("DELETE FROM cl_performance_node WHERE performance_node_id = ?", node.performanceNodeId());
        jdbcTemplate.update("""
                INSERT INTO cl_performance_node (performance_node_id, performance_record_id, contract_id, node_type, node_name, milestone_code, planned_at, due_at, actual_at, node_status, progress_percent, risk_level, issue_count, is_overdue, result_summary, last_result_at, owner_user_id, owner_org_unit_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, node.performanceNodeId(), node.performanceRecordId(), node.contractId(), node.nodeType(), node.nodeName(), node.milestoneCode(),
                node.plannedAt(), node.dueAt(), node.actualAt(), node.nodeStatus(), node.progressPercent(), node.riskLevel(), node.issueCount(),
                node.overdue(), node.resultSummary(), node.lastResultAt(), node.ownerUserId(), node.ownerOrgUnitId());
    }

    private void persistLifecycleSummary(String contractId, Map<String, Object> summary) {
        Map<String, Object> performanceSummary = summary.containsKey("performance_summary") ? map(summary.get("performance_summary")) : summary;
        Map<String, Object> riskSummary = map(summary.get("risk_summary"));
        String currentStage = text(summary, "current_stage", "PERFORMANCE");
        String stageStatus = text(summary, "stage_status", text(performanceSummary, "performance_status", "IN_PROGRESS"));
        jdbcTemplate.update("DELETE FROM cl_lifecycle_summary WHERE contract_id = ?", contractId);
        jdbcTemplate.update("""
                INSERT INTO cl_lifecycle_summary (contract_id, current_stage, stage_status, performance_record_id, performance_status, progress_percent, risk_level, open_node_count, overdue_node_count, issue_count, latest_milestone_code, latest_milestone_at, summary_version, updated_at, change_summary_json, termination_summary_json, archive_summary_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, contractId, currentStage, stageStatus, text(performanceSummary, "performance_record_id", null),
                text(performanceSummary, "performance_status", null), intValue(performanceSummary, "progress_percent", 0), text(riskSummary, "risk_level", "LOW"),
                intValue(performanceSummary, "open_node_count", 0), intValue(performanceSummary, "overdue_node_count", 0), intValue(riskSummary, "issue_count", 0),
                text(summary, "latest_milestone_code", null), Instant.now().toString(), "cl-summary-v1", Instant.now().toString(),
                summaryJson(summary.get("change_summary")), summaryJson(summary.get("termination_summary")), summaryJson(summary.get("archive_summary")));
    }

    private void persistChange(ContractChangeState change) {
        jdbcTemplate.update("DELETE FROM cl_contract_change WHERE change_id = ?", change.changeId());
        jdbcTemplate.update("""
                INSERT INTO cl_contract_change (change_id, contract_id, change_type, change_reason, change_summary, impact_scope_json, effective_date, change_status, workflow_instance_id, approved_at, applied_at, change_result_summary, result_version_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, change.changeId(), change.contractId(), change.changeType(), change.changeReason(), change.changeSummary(), change.impactScope().toString(),
                change.effectiveDate(), change.changeStatus(), change.workflowInstanceId(), change.approvedAt(), change.appliedAt(), change.changeResultSummary(), change.resultVersionNo());
    }

    private void persistTermination(ContractTerminationState termination) {
        jdbcTemplate.update("DELETE FROM cl_contract_termination WHERE termination_id = ?", termination.terminationId());
        jdbcTemplate.update("""
                INSERT INTO cl_contract_termination (termination_id, contract_id, termination_type, termination_reason, termination_summary, requested_termination_date, settlement_summary, termination_status, workflow_instance_id, terminated_at, post_action_status, access_restriction)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, termination.terminationId(), termination.contractId(), termination.terminationType(), termination.terminationReason(), termination.terminationSummary(),
                termination.requestedTerminationDate(), termination.settlementSummary(), termination.terminationStatus(), termination.workflowInstanceId(),
                termination.terminatedAt(), termination.postActionStatus(), termination.accessRestriction());
    }

    private void persistArchive(ArchiveRecordState archive) {
        jdbcTemplate.update("DELETE FROM cl_archive_record WHERE archive_record_id = ?", archive.archiveRecordId());
        jdbcTemplate.update("""
                INSERT INTO cl_archive_record (archive_record_id, contract_id, archive_batch_no, archive_type, archive_reason, input_set_snapshot, archive_status, archive_integrity_status, archive_keeper_user_id, archive_location_code, package_document_asset_id, package_document_version_id, manifest_document_asset_id, manifest_document_version_id, archived_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, archive.archiveRecordId(), archive.contractId(), archive.archiveBatchNo(), archive.archiveType(), archive.archiveReason(), archive.inputSet().toString(),
                archive.archiveStatus(), archive.archiveIntegrityStatus(), archive.archiveKeeperUserId(), archive.archiveLocationCode(),
                text(archive.packageRef(), "document_asset_id", null), text(archive.packageRef(), "document_version_id", null),
                text(archive.manifestRef(), "document_asset_id", null), text(archive.manifestRef(), "document_version_id", null), archive.archivedAt());
    }

    private void persistBorrow(ArchiveBorrowState borrow) {
        jdbcTemplate.update("DELETE FROM cl_archive_borrow_record WHERE borrow_record_id = ?", borrow.borrowRecordId());
        jdbcTemplate.update("""
                INSERT INTO cl_archive_borrow_record (borrow_record_id, contract_id, archive_record_id, archive_batch_no, package_document_asset_id, manifest_document_asset_id, borrow_status, borrow_purpose, requested_by, requested_org_unit_id, requested_at, due_at, returned_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, borrow.borrowRecordId(), borrow.contractId(), borrow.archiveRecordId(), borrow.archiveBatchNo(), text(borrow.packageRef(), "document_asset_id", null),
                text(borrow.manifestRef(), "document_asset_id", null), borrow.borrowStatus(), borrow.borrowPurpose(), borrow.requestedBy(), borrow.requestedOrgUnitId(),
                borrow.requestedAt(), borrow.dueAt(), borrow.returnedAt());
    }

    private void persistLifecycleProcessRef(Map<String, Object> processRef) {
        jdbcTemplate.update("DELETE FROM cl_lifecycle_process_ref WHERE lifecycle_process_ref_id = ?", text(processRef, "lifecycle_process_ref_id", null));
        jdbcTemplate.update("""
                INSERT INTO cl_lifecycle_process_ref (lifecycle_process_ref_id, contract_id, source_resource_type, source_resource_id, workflow_instance_id, process_purpose, process_status_snapshot, last_synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, text(processRef, "lifecycle_process_ref_id", null), text(processRef, "contract_id", null),
                text(processRef, "source_resource_type", null), text(processRef, "source_resource_id", null), text(processRef, "workflow_instance_id", null),
                text(processRef, "process_purpose", null), text(processRef, "process_status_snapshot", null), Instant.now().toString());
    }

    private void persistPerformanceDocumentRef(Map<String, Object> documentRef) {
        if (documentRef == null || documentRef.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM cl_lifecycle_document_ref WHERE lifecycle_document_ref_id = ?", text(documentRef, "lifecycle_document_ref_id", null));
        jdbcTemplate.update("""
                INSERT INTO cl_lifecycle_document_ref (lifecycle_document_ref_id, contract_id, source_resource_type, source_resource_id, document_role, document_asset_id, document_version_id, is_primary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, text(documentRef, "lifecycle_document_ref_id", null), text(documentRef, "contract_id", null),
                text(documentRef, "source_resource_type", null), text(documentRef, "source_resource_id", null), text(documentRef, "document_role", null),
                text(documentRef, "document_asset_id", null), text(documentRef, "document_version_id", null), bool(documentRef, "is_primary", true), Instant.now().toString());
    }

    private String lifecycleDocumentRefId(Map<String, Object> documentRef) {
        return documentRef == null || documentRef.isEmpty() ? null : text(documentRef, "lifecycle_document_ref_id", null);
    }

    private void appendLifecycleTimeline(String contractId, String eventType, String sourceResourceType, String sourceResourceId,
                                         String milestoneCode, String actorUserId, String eventResult, String documentRefId, String traceId) {
        String eventId = "cl-time-" + UUID.randomUUID();
        String dedupeKey = dedupeKey("timeline", eventType, sourceResourceId, traceId);
        Map<String, Object> event = lifecycleEvent(eventId, contractId, eventType, sourceResourceType, sourceResourceId, milestoneCode,
                dedupeKey, actorUserId, eventResult, documentRefId, traceId);
        lifecycleTimelineEvents.add(event);
        jdbcTemplate.update("""
                INSERT INTO cl_lifecycle_timeline_event (timeline_event_id, contract_id, event_type, source_resource_type, source_resource_id, milestone_code, dedupe_key, actor_user_id, event_result, related_document_ref_id, trace_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, contractId, eventType, sourceResourceType, sourceResourceId, milestoneCode, dedupeKey, actorUserId,
                eventResult, documentRefId, traceId, text(event, "occurred_at", null));
    }

    private void appendLifecycleAudit(String contractId, String eventType, String sourceResourceType, String sourceResourceId,
                                      String actorUserId, String resultStatus, String eventResult, String documentRefId, String traceId) {
        String eventId = "cl-audit-" + UUID.randomUUID();
        String dedupeKey = dedupeKey("audit", eventType, sourceResourceId, traceId);
        Map<String, Object> event = lifecycleEvent(eventId, contractId, eventType, sourceResourceType, sourceResourceId, null,
                dedupeKey, actorUserId, eventResult, documentRefId, traceId);
        event.put("result_status", resultStatus);
        lifecycleAuditEvents.add(event);
        jdbcTemplate.update("""
                INSERT INTO cl_lifecycle_audit_event (audit_event_id, contract_id, event_type, source_resource_type, source_resource_id, actor_user_id, result_status, event_result, dedupe_key, related_document_ref_id, trace_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, contractId, eventType, sourceResourceType, sourceResourceId, actorUserId, resultStatus, eventResult,
                dedupeKey, documentRefId, traceId, text(event, "occurred_at", null));
    }

    private Map<String, Object> lifecycleEvent(String eventId, String contractId, String eventType, String sourceResourceType,
                                               String sourceResourceId, String milestoneCode, String dedupeKey, String actorUserId,
                                               String eventResult, String documentRefId, String traceId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", eventId);
        event.put("contract_id", contractId);
        event.put("event_type", eventType);
        event.put("source_resource_type", sourceResourceType);
        event.put("source_resource_id", sourceResourceId);
        event.put("milestone_code", milestoneCode);
        event.put("dedupe_key", dedupeKey);
        event.put("actor_user_id", actorUserId);
        event.put("event_result", eventResult);
        event.put("related_document_ref_id", documentRefId);
        event.put("trace_id", traceId);
        event.put("visible_to_search", true);
        event.put("visible_to_ai", true);
        event.put("visible_to_notify", true);
        event.put("occurred_at", Instant.now().toString());
        return event;
    }

    private String dedupeKey(String scope, String eventType, String sourceResourceId, String traceId) {
        return scope + ":" + eventType + ":" + sourceResourceId + ":" + (traceId == null ? UUID.randomUUID() : traceId);
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

    private ContractChangeState requireChange(String changeId) {
        ContractChangeState change = contractChanges.get(changeId);
        if (change == null) {
            throw new IllegalArgumentException("change_id 不存在: " + changeId);
        }
        return change;
    }

    private ContractTerminationState requireTermination(String terminationId) {
        ContractTerminationState termination = contractTerminations.get(terminationId);
        if (termination == null) {
            throw new IllegalArgumentException("termination_id 不存在: " + terminationId);
        }
        return termination;
    }

    private ArchiveRecordState requireArchive(String archiveRecordId) {
        ArchiveRecordState archive = archiveRecords.get(archiveRecordId);
        if (archive == null) {
            throw new IllegalArgumentException("archive_record_id 不存在: " + archiveRecordId);
        }
        return archive;
    }

    private ArchiveBorrowState requireBorrow(String borrowRecordId) {
        ArchiveBorrowState borrow = archiveBorrows.get(borrowRecordId);
        if (borrow == null) {
            throw new IllegalArgumentException("borrow_record_id 不存在: " + borrowRecordId);
        }
        return borrow;
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
        body.put("change_summary", changeSummaries.get(contract.contractId()));
        body.put("termination_summary", terminationSummaries.get(contract.contractId()));
        body.put("archive_summary", archiveSummaries.get(contract.contractId()));
        return body;
    }

    private List<Map<String, Object>> classificationChain(Map<String, Object> request) {
        Object raw = request.get("classification_chain");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .<Map<String, Object>>map(item -> new LinkedHashMap<>((Map<String, Object>) item))
                    .toList();
        }
        return List.of(Map.of("category_code", text(request, "category_code", "GENERAL"), "category_name", text(request, "category_name", "通用合同")));
    }

    private Map<String, Object> semanticReferenceRefs(Map<String, Object> request) {
        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("clause_library_code", text(request, "clause_library_code", "clause-lib-default"));
        refs.put("template_library_code", text(request, "template_library_code", "tpl-lib-default"));
        return refs;
    }

    private Map<String, Object> classificationMasterLink(String contractId) {
        List<Map<String, Object>> chain = contractClassificationChains.getOrDefault(contractId, classificationChain(Map.of()));
        String categoryPath = chain.stream().map(item -> text(item, "category_code", "GENERAL")).reduce((left, right) -> left + "/" + right).orElse("GENERAL");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("classification_chain", chain);
        body.put("category_path", categoryPath);
        body.put("source_of_truth", "contract-core");
        body.put("stable_link_status", "ACTIVE");
        return body;
    }

    private List<Map<String, Object>> capabilityBindings(DocumentAssetState asset) {
        return List.of(
                capabilityBinding(asset, "OCR", "document-center.ocr.input"),
                capabilityBinding(asset, "SEARCH", "document-center.search.input"),
                capabilityBinding(asset, "AI_APPLICATION", "document-center.ai.input"));
    }

    private Map<String, Object> capabilityBinding(DocumentAssetState asset, String capabilityCode, String bindingCode) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("capability_code", capabilityCode);
        binding.put("binding_code", bindingCode);
        binding.put("document_asset_id", asset.documentAssetId());
        binding.put("document_version_id", asset.currentVersionId());
        String resultId = "OCR".equals(capabilityCode) ? currentOcrResultByDocumentVersion.get(asset.currentVersionId()) : null;
        binding.put("binding_status", "OCR".equals(capabilityCode) && resultId == null ? "PENDING" : "READY");
        if (resultId != null) {
            OcrResultState result = requireOcrResult(resultId);
            binding.put("result_aggregate_id", result.ocrResultAggregateId());
            binding.put("result_status", result.resultStatus());
            binding.put("quality_profile_code", result.qualityProfileCode());
            binding.put("quality_score", result.qualityScore());
            binding.put("compensation_status", "BOUND");
        }
        binding.put("source_of_truth", "document-center");
        return binding;
    }

    private void appendDocumentConsumerEvent(String eventType, DocumentAssetState asset, String documentVersionId, String traceId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", "dc-event-" + UUID.randomUUID());
        event.put("event_type", eventType);
        event.put("document_asset_id", asset.documentAssetId());
        event.put("document_version_id", documentVersionId);
        event.put("owner_type", asset.ownerType());
        event.put("owner_id", asset.ownerId());
        event.put("consumer_scope", "BATCH4_INTELLIGENT_APPLICATIONS");
        event.put("delivery_status", "READY");
        event.put("trace_id", traceId);
        event.put("occurred_at", Instant.now().toString());
        documentConsumerEvents.add(event);
    }

    private Map<String, Object> ocrJobBody(OcrJobState job, boolean replayed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ocr_job_id", job.ocrJobId());
        body.put("contract_id", job.contractId());
        body.put("document_asset_id", job.documentAssetId());
        body.put("document_version_id", job.documentVersionId());
        body.put("input_content_fingerprint", job.inputContentFingerprint());
        body.put("job_purpose", job.jobPurpose());
        body.put("job_status", job.jobStatus());
        body.put("quality_profile_code", job.qualityProfileCode());
        body.put("engine_route", Map.of("engine_route_code", job.engineRouteCode(), "engine_profile_code", "GENERAL_TEXT", "capability_tags", List.of("TEXT", "LAYOUT", "TABLE", "SEAL", "FIELD", "LANGUAGE")));
        body.put("current_attempt_no", job.currentAttemptNo());
        body.put("max_attempt_no", job.maxAttemptNo());
        body.put("ocr_result_aggregate_id", job.resultAggregateId());
        body.put("failure_code", job.failureCode());
        body.put("failure_reason", job.failureReason());
        body.put("idempotency_replayed", replayed);
        body.put("task_center_ref", Map.of("platform_job_id", job.platformJobId(), "job_type", "IA_OCR_RECOGNITION", "job_status", "PENDING"));
        if (job.resultAggregateId() != null) {
            body.put("result", ocrResultBody(requireOcrResult(job.resultAggregateId())));
        }
        body.put("retry_facts", ocrRetryFacts.getOrDefault(job.ocrJobId(), List.of()));
        body.put("audit_events", ocrAuditEvents.getOrDefault(job.ocrJobId(), List.of()));
        body.put("trace_id", job.traceId());
        return body;
    }

    private Map<String, Object> ocrResultBody(OcrResultState result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ocr_result_aggregate_id", result.ocrResultAggregateId());
        body.put("ocr_job_id", result.ocrJobId());
        body.put("contract_id", result.contractId());
        body.put("document_asset_id", result.documentAssetId());
        body.put("document_version_id", result.documentVersionId());
        body.put("result_status", result.resultStatus());
        body.put("result_schema_version", result.resultSchemaVersion());
        body.put("quality_profile_code", result.qualityProfileCode());
        body.put("full_text_ref", result.fullTextRef());
        body.put("text_layer", Map.of("pages", result.textLayer()));
        body.put("layout_blocks", result.layoutBlocks());
        body.put("table_regions", result.tableRegions());
        body.put("seal_regions", result.sealRegions());
        body.put("field_candidates", result.fieldCandidates());
        body.put("language_segments", result.languageSegments());
        body.put("page_summary", result.pageSummary());
        body.put("quality_score", result.qualityScore());
        body.put("content_fingerprint", result.contentFingerprint());
        body.put("superseded_by_result_id", result.supersededByResultId());
        body.put("default_consumable", result.defaultConsumable());
        return body;
    }

    private void persistOcrJob(OcrJobState job) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into ia_ocr_job
                (ocr_job_id, contract_id, document_asset_id, document_version_id, input_content_fingerprint, job_purpose, job_status,
                 language_hint_json, quality_profile_code, engine_route_code, current_attempt_no, max_attempt_no, result_aggregate_id,
                 failure_code, failure_reason, idempotency_key, trace_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, job.ocrJobId(), job.contractId(), job.documentAssetId(), job.documentVersionId(), job.inputContentFingerprint(), job.jobPurpose(),
                job.jobStatus(), job.languageHintJson(), job.qualityProfileCode(), job.engineRouteCode(), job.currentAttemptNo(), job.maxAttemptNo(),
                job.resultAggregateId(), job.failureCode(), job.failureReason(), job.idempotencyKey(), job.traceId(), Timestamp.from(now), Timestamp.from(now));
    }

    private void persistOcrResult(OcrResultState result) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into ia_ocr_result_aggregate
                (ocr_result_aggregate_id, ocr_job_id, contract_id, document_asset_id, document_version_id, result_status, result_schema_version,
                 quality_profile_code, full_text_ref, page_summary_json, layout_block_ref, field_candidate_ref, language_segment_ref,
                 table_payload_ref, seal_payload_ref, citation_payload_ref, quality_score, content_fingerprint, superseded_by_result_id,
                 default_consumable, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, result.ocrResultAggregateId(), result.ocrJobId(), result.contractId(), result.documentAssetId(), result.documentVersionId(), result.resultStatus(),
                result.resultSchemaVersion(), result.qualityProfileCode(), result.fullTextRef(), json(result.pageSummary()), "ocr://layout/" + result.ocrResultAggregateId(),
                "ocr://fields/" + result.ocrResultAggregateId(), "ocr://language/" + result.ocrResultAggregateId(), "ocr://tables/" + result.ocrResultAggregateId(),
                "ocr://seals/" + result.ocrResultAggregateId(), "ocr://citations/" + result.ocrResultAggregateId(), result.qualityScore(), result.contentFingerprint(),
                result.supersededByResultId(), result.defaultConsumable(), Timestamp.from(now), Timestamp.from(now));
    }

    private void persistOcrResultParts(OcrResultState result) {
        for (Map<String, Object> page : result.textLayer()) {
            jdbcTemplate.update("""
                    insert into ia_ocr_text_layer
                    (text_layer_id, ocr_result_aggregate_id, page_no, text_content, bbox_json, confidence_score, language_code, source_engine_code)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, text(page, "text_layer_id", "ocr-text-" + UUID.randomUUID()), result.ocrResultAggregateId(), intValue(page, "page_no", 1),
                    text(page, "text", ""), json(page.get("bbox")), doubleValue(page, "confidence_score", 0.95), text(page, "language_code", "zh-CN"), text(page, "source_engine_code", "OCR_BASELINE"));
        }
        for (Map<String, Object> block : result.layoutBlocks()) {
            jdbcTemplate.update("""
                    insert into ia_ocr_layout_block
                    (layout_block_id, ocr_result_aggregate_id, page_no, block_type, text_excerpt, bbox_json, reading_order, confidence_score, parent_block_id, source_engine_code)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, text(block, "layout_block_id", "ocr-layout-" + UUID.randomUUID()), result.ocrResultAggregateId(), intValue(block, "page_no", 1),
                    text(block, "block_type", "PARAGRAPH"), text(block, "text_excerpt", null), json(block.get("bbox")), intValue(block, "reading_order", 1),
                    doubleValue(block, "confidence_score", 0.94), text(block, "parent_block_id", null), text(block, "source_engine_code", "OCR_BASELINE"));
        }
        for (Map<String, Object> table : result.tableRegions()) {
            jdbcTemplate.update("""
                    insert into ia_ocr_table_region
                    (table_region_id, ocr_result_aggregate_id, page_no, bbox_json, row_count, column_count, cell_list_json, header_candidate_list_json, table_confidence_score)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, text(table, "table_region_id", "ocr-table-" + UUID.randomUUID()), result.ocrResultAggregateId(), intValue(table, "page_no", 1),
                    json(table.get("bbox")), intValue(table, "row_count", 1), intValue(table, "column_count", 1), json(table.get("cell_list")),
                    json(table.get("header_candidate_list")), doubleValue(table, "table_confidence_score", 0.92));
        }
        for (Map<String, Object> seal : result.sealRegions()) {
            jdbcTemplate.update("""
                    insert into ia_ocr_seal_region
                    (seal_region_id, ocr_result_aggregate_id, page_no, bbox_json, seal_text_candidate, seal_shape, color_hint, overlap_signature_flag, confidence_score)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, text(seal, "seal_region_id", "ocr-seal-" + UUID.randomUUID()), result.ocrResultAggregateId(), intValue(seal, "page_no", 1),
                    json(seal.get("bbox")), text(seal, "seal_text_candidate", null), text(seal, "seal_shape", "UNKNOWN"), text(seal, "color_hint", null),
                    bool(seal, "overlap_signature_flag", false), doubleValue(seal, "confidence_score", 0.9));
        }
        for (Map<String, Object> field : result.fieldCandidates()) {
            jdbcTemplate.update("""
                    insert into ia_ocr_field_candidate
                    (field_candidate_id, ocr_result_aggregate_id, field_type, candidate_value, normalized_value, source_layout_block_id, page_no,
                     bbox_json, confidence_score, quality_profile_code, field_threshold_code, evidence_text, candidate_status)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, text(field, "field_candidate_id", "ocr-field-" + UUID.randomUUID()), result.ocrResultAggregateId(), text(field, "field_type", "CONTRACT_NO"),
                    text(field, "candidate_value", ""), text(field, "normalized_value", null), text(field, "source_layout_block_id", null), intValue(field, "page_no", 1),
                    json(field.get("bbox")), doubleValue(field, "confidence_score", 0.9), result.qualityProfileCode(), text(field, "field_threshold_code", "contract_no_min_field_confidence_score"),
                    text(field, "evidence_text", null), text(field, "candidate_status", "CANDIDATE"));
        }
        for (Map<String, Object> segment : result.languageSegments()) {
            jdbcTemplate.update("""
                    insert into ia_ocr_language_segment
                    (language_segment_id, ocr_result_aggregate_id, page_no, layout_block_id, language_code, text_range_json, bbox_json, confidence_score, normalization_profile_code)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, text(segment, "language_segment_id", "ocr-lang-" + UUID.randomUUID()), result.ocrResultAggregateId(), intValue(segment, "page_no", 1),
                    text(segment, "layout_block_id", null), text(segment, "language_code", "zh-CN"), json(segment.get("text_range")), json(segment.get("bbox")),
                    doubleValue(segment, "confidence_score", 0.96), text(segment, "normalization_profile_code", "I18N_BASELINE"));
        }
    }

    private Map<String, Object> appendOcrRetry(OcrJobState job, int attemptNo, String failureCode, String failureReason, boolean retryable) {
        String retryFactId = "ocr-retry-" + UUID.randomUUID();
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("retry_fact_id", retryFactId);
        fact.put("ocr_job_id", job.ocrJobId());
        fact.put("attempt_no", attemptNo);
        fact.put("failure_code", failureCode);
        fact.put("failure_reason", failureReason);
        fact.put("retryable", retryable);
        fact.put("trace_id", job.traceId());
        fact.put("created_at", Instant.now().toString());
        ocrRetryFacts.computeIfAbsent(job.ocrJobId(), ignored -> new ArrayList<>()).add(fact);
        jdbcTemplate.update("""
                insert into ia_ocr_retry_fact
                (retry_fact_id, ocr_job_id, attempt_no, failure_code, failure_reason, retryable, next_retry_after, trace_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, retryFactId, job.ocrJobId(), attemptNo, failureCode, failureReason, retryable, Timestamp.from(Instant.now().plusSeconds(30L * attemptNo)), job.traceId(), Timestamp.from(Instant.now()));
        return fact;
    }

    private Map<String, Object> appendOcrAudit(OcrJobState job, OcrResultState result, String actionType, String resultStatus, String failureReason) {
        String auditId = "ocr-audit-" + UUID.randomUUID();
        String resultId = result == null ? job.resultAggregateId() : result.ocrResultAggregateId();
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("audit_event_id", auditId);
        audit.put("ocr_job_id", job.ocrJobId());
        audit.put("ocr_result_aggregate_id", resultId);
        audit.put("contract_id", job.contractId());
        audit.put("document_asset_id", job.documentAssetId());
        audit.put("document_version_id", job.documentVersionId());
        audit.put("content_fingerprint", job.inputContentFingerprint());
        audit.put("actor_id", job.actorId());
        audit.put("trace_id", job.traceId());
        audit.put("action_type", actionType);
        audit.put("result_status", resultStatus);
        audit.put("failure_reason", failureReason);
        audit.put("occurred_at", Instant.now().toString());
        ocrAuditEvents.computeIfAbsent(job.ocrJobId(), ignored -> new ArrayList<>()).add(audit);
        requireDocumentAsset(job.documentAssetId()).auditRecords().add(new LinkedHashMap<>(audit));
        jdbcTemplate.update("""
                insert into ia_ocr_audit_event
                (audit_event_id, ocr_job_id, ocr_result_aggregate_id, contract_id, document_asset_id, document_version_id,
                 content_fingerprint, actor_id, trace_id, action_type, result_status, failure_reason, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, auditId, job.ocrJobId(), resultId, job.contractId(), job.documentAssetId(), job.documentVersionId(), job.inputContentFingerprint(),
                job.actorId(), job.traceId(), actionType, resultStatus, failureReason, Timestamp.from(Instant.now()));
        return audit;
    }

    private String createPlatformJob(String jobType, String sourceModule, String consumerModule, String resourceType, String resourceId,
                                     String businessObjectType, String businessObjectId, String traceId, String runnerCode) {
        String platformJobId = "platform-job-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into platform_job
                (platform_job_id, job_type, job_status, source_module, consumer_module, resource_type, resource_id,
                 business_object_type, business_object_id, priority, attempt_no, max_attempts, runner_code, trace_id, created_at, updated_at)
                values (?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, 50, 0, 3, ?, ?, ?, ?)
                """, platformJobId, jobType, sourceModule, consumerModule, resourceType, resourceId, businessObjectType, businessObjectId,
                runnerCode, traceId, Timestamp.from(now), Timestamp.from(now));
        return platformJobId;
    }

    private String contentFingerprint(DocumentVersionState version) {
        String seed = version.documentVersionId() + ":" + version.fileUploadToken() + ":" + version.versionNo();
        return "sha256:" + Integer.toHexString(seed.hashCode());
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
        taskCenterRef.put("process_id", process.processId());
        taskCenterRef.put("source_resource_type", "APPROVAL_TASK");
        taskCenterRef.put("source_resource_id", taskId);
        taskCenterRef.put("business_object_type", "CONTRACT");
        taskCenterRef.put("business_object_id", process.contractId());
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

    private OcrJobState requireOcrJob(String ocrJobId) {
        OcrJobState job = ocrJobs.get(ocrJobId);
        if (job == null) {
            throw new IllegalArgumentException("ocr_job_id 不存在: " + ocrJobId);
        }
        return job;
    }

    private OcrResultState requireOcrResult(String resultId) {
        OcrResultState result = ocrResults.get(resultId);
        if (result == null) {
            throw new IllegalArgumentException("ocr_result_aggregate_id 不存在: " + resultId);
        }
        return result;
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

    private List<String> listStrings(Object value) {
        return value instanceof List<?> source ? source.stream().map(Object::toString).toList() : List.of();
    }

    private List<Object> listObjects(Object value) {
        return value instanceof List<?> source ? new ArrayList<>(source) : List.of();
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

    private double doubleValue(Map<String, Object> request, String field, double defaultValue) {
        Object value = request.get(field);
        return value == null ? defaultValue : Double.parseDouble(value.toString());
    }

    private String text(Map<String, Object> request, String field, String defaultValue) {
        Object value = request.get(field);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private String summaryJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("生命周期摘要无法序列化", exception);
        }
    }

    private Map<String, Object> mapFromJson(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, LinkedHashMap.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 对象无法反序列化", exception);
        }
    }

    private List<Map<String, Object>> listMapsFromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, List.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 列表无法反序列化", exception);
        }
    }

    private String json(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("对象无法序列化", exception);
        }
    }

    private interface OcrEngineAdapter {
        OcrResultState recognize(OcrJobState job, String contractName, boolean partialResult);
    }

    private static class BaselineOcrEngineAdapter implements OcrEngineAdapter {
        @Override
        public OcrResultState recognize(OcrJobState job, String contractName, boolean partialResult) {
            String resultId = "ocr-result-" + UUID.randomUUID();
            String layoutBlockId = "ocr-layout-" + UUID.randomUUID();
            Map<String, Object> bbox = bbox(0, 0, 900, 120);
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("text_layer_id", "ocr-text-" + UUID.randomUUID());
            page.put("page_no", 1);
            page.put("text", contractName + " 示例识别文本");
            page.put("bbox", bbox);
            page.put("confidence_score", partialResult ? 0.72 : 0.96);
            page.put("language_code", "zh-CN");
            page.put("source_engine_code", "OCR_BASELINE");

            Map<String, Object> block = new LinkedHashMap<>();
            block.put("layout_block_id", layoutBlockId);
            block.put("page_no", 1);
            block.put("block_type", "PARAGRAPH");
            block.put("text_excerpt", contractName + " 示例识别文本");
            block.put("bbox", bbox);
            block.put("reading_order", 1);
            block.put("confidence_score", partialResult ? 0.72 : 0.94);
            block.put("source_engine_code", "OCR_BASELINE");

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("table_region_id", "ocr-table-" + UUID.randomUUID());
            table.put("page_no", 1);
            table.put("bbox", bbox(0, 160, 600, 260));
            table.put("row_count", 1);
            table.put("column_count", 2);
            table.put("cell_list", List.of(Map.of("row", 1, "column", 1, "text", "合同编号", "confidence_score", 0.93)));
            table.put("header_candidate_list", List.of("合同编号"));
            table.put("table_confidence_score", partialResult ? 0.7 : 0.92);

            Map<String, Object> seal = new LinkedHashMap<>();
            seal.put("seal_region_id", "ocr-seal-" + UUID.randomUUID());
            seal.put("page_no", 1);
            seal.put("bbox", bbox(650, 700, 220, 220));
            seal.put("seal_text_candidate", "合同专用章");
            seal.put("seal_shape", "ROUND");
            seal.put("color_hint", "RED");
            seal.put("overlap_signature_flag", false);
            seal.put("confidence_score", partialResult ? 0.68 : 0.91);

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("field_candidate_id", "ocr-field-" + UUID.randomUUID());
            field.put("field_type", "CONTRACT_NO");
            field.put("candidate_value", "CMP-OCR-001");
            field.put("normalized_value", "CMP-OCR-001");
            field.put("source_layout_block_id", layoutBlockId);
            field.put("page_no", 1);
            field.put("bbox", bbox(120, 20, 200, 30));
            field.put("confidence_score", partialResult ? 0.67 : 0.9);
            field.put("field_threshold_code", "contract_no_min_field_confidence_score");
            field.put("evidence_text", "合同编号 CMP-OCR-001");
            field.put("candidate_status", partialResult ? "LOW_CONFIDENCE" : "CANDIDATE");

            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("language_segment_id", "ocr-lang-" + UUID.randomUUID());
            segment.put("page_no", 1);
            segment.put("layout_block_id", layoutBlockId);
            segment.put("language_code", "zh-CN");
            segment.put("text_range", Map.of("start", 0, "end", contractName.length()));
            segment.put("bbox", bbox);
            segment.put("confidence_score", 0.96);
            segment.put("normalization_profile_code", "I18N_BASELINE");

            List<Map<String, Object>> pageSummary = List.of(Map.of("page_no", 1, "page_width", 1000, "page_height", 1400, "rotation", 0, "quality", partialResult ? "LOW" : "GOOD"));
            String status = partialResult ? "PARTIAL" : "READY";
            double qualityScore = partialResult ? 0.71 : 0.96;
            return new OcrResultState(resultId, job.ocrJobId(), job.contractId(), job.documentAssetId(), job.documentVersionId(), status,
                    "ocr-result-v1", job.qualityProfileCode(), "ocr://text/" + resultId, List.of(page), List.of(block), List.of(table), List.of(seal),
                    List.of(field), List.of(segment), pageSummary, qualityScore, job.inputContentFingerprint(), null, true, Instant.now().toString(), Instant.now().toString());
        }

        private static Map<String, Object> bbox(int x, int y, int width, int height) {
            Map<String, Object> bbox = new LinkedHashMap<>();
            bbox.put("x", x);
            bbox.put("y", y);
            bbox.put("width", width);
            bbox.put("height", height);
            bbox.put("unit", "PIXEL");
            bbox.put("page_width", 1000);
            bbox.put("page_height", 1400);
            bbox.put("rotation", 0);
            return bbox;
        }
    }

    private record SearchSourceEnvelopeState(String envelopeId, String docType, String sourceObjectId, String sourceVersionDigest,
                                             String contractId, String documentAssetId, String documentVersionId, String semanticRefId,
                                             String sourceAnchorJson, String titleText, String bodyText, String keywordText,
                                             String ownerOrgUnitId, String localeCode, String admissionStatus, String createdAt) {
    }

    private record SearchDocumentState(String searchDocId, String docType, String sourceObjectId, String sourceAnchorJson,
                                       String sourceVersionDigest, String contractId, String documentAssetId, String documentVersionId,
                                       String semanticRefId, String titleText, String bodyText, String keywordText,
                                       String filterPayloadJson, String sortPayloadJson, String localeCode, String visibilityScopeJson,
                                       String exposureStatus, int rebuildGeneration, String indexedAt) {
    }

    private record SearchResultSetState(String resultSetId, String resultStatus, String searchQueryJson, String itemPayloadJson,
                                         String facetPayloadJson, int total, String rankingProfileCode, String expiresAt,
                                         boolean cacheHitFlag, String permissionScopeDigest, String actorId, int rebuildGeneration,
                                         String stableOrderDigest, String createdAt) {
    }

    private record CandidateRankingSnapshotState(String rankingSnapshotId, String aiApplicationJobId, String applicationType,
                                                 String contractId, String documentVersionId, String sourceDigest,
                                                 String rankingProfileCode, String rankingProfileVersion,
                                                 String qualityProfileCode, String qualityProfileVersion,
                                                 String snapshotStatus, String selectedCandidateJson, String candidateListJson,
                                                 String expiresAt, String traceId, String createdAt) {
        CandidateRankingSnapshotState withStatus(String status) {
            return new CandidateRankingSnapshotState(rankingSnapshotId, aiApplicationJobId, applicationType, contractId, documentVersionId,
                    sourceDigest, rankingProfileCode, rankingProfileVersion, qualityProfileCode, qualityProfileVersion, status,
                    selectedCandidateJson, candidateListJson, expiresAt, traceId, createdAt);
        }
    }

    private record QualityEvaluationReportState(String qualityEvaluationId, String rankingSnapshotId, String applicationType,
                                                String qualityProfileCode, String qualityProfileVersion, String qualityTier,
                                                String releaseDecision, List<String> decisionReasonCodes, double coverageScore,
                                                double citationValidityScore, double consistencyScore, double completenessScore,
                                                double publishabilityScore, String createdAt) {
    }

    private record CandidateWritebackGateState(String resultId, String rankingSnapshotId, String qualityEvaluationId,
                                               String releaseDecision, String writebackGateStatus, boolean writebackAllowed,
                                               String traceId, String createdAt) {
    }

    private record QualityDecision(String qualityTier, String releaseDecision, List<String> reasonCodes, double coverage,
                                   double citation, double consistency, double completeness, double publishability) {
    }

    private record AiApplicationJobState(String aiApplicationJobId, String applicationType, String contractId, String documentVersionId,
                                          String jobStatus, String contextAssemblyJobId, String resultContextId, String agentTaskId,
                                         String agentResultId, String idempotencyKey, String scopeDigest, String failureCode,
                                         String failureReason, String traceId, String createdAt, String updatedAt) {
    }

    private record AiApplicationResultState(String resultId, String aiApplicationJobId, String agentTaskId, String agentResultId,
                                            String applicationType, String resultStatus, Map<String, Object> structuredPayload,
                                            List<Map<String, Object>> citationList, double evidenceCoverageRatio, String guardrailDecision,
                                            String guardrailFailureCode, boolean confirmationRequiredFlag, boolean writebackAllowedFlag,
                                            List<String> riskFlagList, String createdAt) {
    }

    private record ProtectedResultSnapshotState(String protectedResultSnapshotId, String aiApplicationJobId, String resultId,
                                                String agentTaskId, String agentResultId, String guardrailDecision,
                                                String guardrailFailureCode, boolean confirmationRequiredFlag,
                                                String protectedPayloadRef, Map<String, Object> protectedPayload,
                                                String expiresAt, String createdAt) {
    }

    private record GuardrailOutcome(String decision, String failureCode, Map<String, Object> payload,
                                    List<Map<String, Object>> citations, List<String> riskFlags) {
    }

    private record AgentOsBinding(String taskId, String runId, String resultId, String failureCode) {
    }

    private record OcrInput(DocumentAssetState asset, DocumentVersionState documentVersion) {
    }

    private record OcrJobState(String ocrJobId, String contractId, String documentAssetId, String documentVersionId,
                               String inputContentFingerprint, String jobPurpose, String jobStatus, String languageHintJson,
                               String qualityProfileCode, String engineRouteCode, int currentAttemptNo, int maxAttemptNo,
                               String resultAggregateId, String failureCode, String failureReason, String idempotencyKey,
                               String traceId, String actorId, String platformJobId, String createdAt, String updatedAt) {
    }

    private record OcrResultState(String ocrResultAggregateId, String ocrJobId, String contractId, String documentAssetId,
                                  String documentVersionId, String resultStatus, String resultSchemaVersion, String qualityProfileCode,
                                  String fullTextRef, List<Map<String, Object>> textLayer, List<Map<String, Object>> layoutBlocks,
                                  List<Map<String, Object>> tableRegions, List<Map<String, Object>> sealRegions,
                                  List<Map<String, Object>> fieldCandidates, List<Map<String, Object>> languageSegments,
                                  List<Map<String, Object>> pageSummary, double qualityScore, String contentFingerprint,
                                  String supersededByResultId, boolean defaultConsumable, String createdAt, String updatedAt) {
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

    private record ContractChangeState(String changeId, String contractId, String changeType, String changeReason,
                                       String changeSummary, Map<String, Object> impactScope, String effectiveDate,
                                       String changeStatus, String workflowInstanceId, Map<String, Object> documentRef,
                                       Map<String, Object> processRef, String approvedAt, String appliedAt,
                                       String changeResultSummary, int resultVersionNo) {
    }

    private record ContractTerminationState(String terminationId, String contractId, String terminationType,
                                            String terminationReason, String terminationSummary, String requestedTerminationDate,
                                            String settlementSummary, String terminationStatus, String workflowInstanceId,
                                            Map<String, Object> documentRef, Map<String, Object> processRef, String terminatedAt,
                                            String postActionStatus, String accessRestriction) {
    }

    private record ArchiveRecordState(String archiveRecordId, String contractId, String archiveBatchNo, String archiveType,
                                      String archiveReason, List<String> inputSet, String archiveStatus,
                                      String archiveIntegrityStatus, String archiveKeeperUserId, String archiveLocationCode,
                                      Map<String, Object> packageRef, Map<String, Object> manifestRef, String archivedAt) {
    }

    private record ArchiveBorrowState(String borrowRecordId, String contractId, String archiveRecordId, String archiveBatchNo,
                                      Map<String, Object> packageRef, Map<String, Object> manifestRef, String borrowStatus,
                                      String borrowPurpose, String requestedBy, String requestedOrgUnitId, String requestedAt,
                                      String dueAt, String returnedAt) {
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
