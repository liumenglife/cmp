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
                text(request, "document_title", text(request, "document_name", null)), "FIRST_VERSION_WRITTEN", "NOT_REQUIRED", "NOT_GENERATED", 1, auditRecords);
        documentAssets.put(documentAssetId, asset);
        documentVersions.put(documentVersionId, new DocumentVersionState(documentVersionId, documentAssetId, 1, null, null,
                text(request, "version_label", "V1"), "首版写入", "ACTIVE", text(request, "file_upload_token", null), text(request, "source_channel", "MANUAL_UPLOAD")));

        bindContractDocument(contract, asset, text(request, "trace_id", null));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract_id", contractId);
        body.putAll(documentAssetBody(asset));
        body.put("document_version_id", asset.currentVersionId());
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
                asset.documentStatus(), asset.encryptionStatus(), asset.previewStatus(), versionNo, copyEvents(asset.auditRecords()));
        updated.auditRecords().add(event("DOCUMENT_VERSION_APPENDED", versionId, text(request, "trace_id", null)));
        documentAssets.put(documentAssetId, updated);
        refreshContractDocumentRef(updated, text(request, "trace_id", null));
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
            Map<String, Object> body = error("WORKFLOW_NODE_BINDING_REQUIRED", "审批节点必须绑定组织架构对象");
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
        Map<String, Object> approvalSummary = approvalSummary(processId, processStatus, result);
        ProcessState updatedProcess = new ProcessState(processId, process.contractId(), process.documentAssetId(), process.documentVersionId(), processStatus, approvalSummary);
        processes.put(processId, updatedProcess);

        ContractState updatedContract = new ContractState(contract.contractId(), contract.contractNo(), contract.contractName(), contractStatus,
                contract.ownerOrgUnitId(), contract.ownerUserId(), contract.amount(), contract.currency(), contract.currentDocument(),
                copyDocuments(contract.attachments()), approvalSummary, processId, copyEvents(contract.events()));
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
        body.put("attachment_summaries", contract.attachments().stream().map(this::documentBody).toList());
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
        body.put("attachment_summaries", contract.attachments().stream().map(this::documentBody).toList());
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
                error.put("message", "审批节点缺少组织绑定");
                errors.add(error);
            }
        }
        return errors;
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

    private DocumentAssetState requireDocumentAsset(String documentAssetId) {
        DocumentAssetState asset = documentAssets.get(documentAssetId);
        if (asset == null) {
            throw new IllegalArgumentException("document_asset_id 不存在: " + documentAssetId);
        }
        return asset;
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
        if ("ATTACHMENT".equals(asset.documentRole())) {
            attachments.removeIf(ref -> asset.documentAssetId().equals(ref.documentAssetId()));
            attachments.add(document);
        } else {
            currentDocument = document;
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
        if ("ATTACHMENT".equals(asset.documentRole())) {
            attachments.add(document);
            eventType = "ATTACHMENT_BOUND";
        } else {
            currentDocument = document;
            eventType = "DOCUMENT_BOUND";
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
                                String processStatus, Map<String, Object> approvalSummary) {
    }
}
