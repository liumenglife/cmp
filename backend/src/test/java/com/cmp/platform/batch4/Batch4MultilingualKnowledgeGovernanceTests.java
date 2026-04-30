package com.cmp.platform.batch4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Batch4MultilingualKnowledgeGovernanceTests {

    private static final String I18N_GOVERNANCE_PERMISSION = "I18N_GOVERNANCE_MANAGE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanMultilingualTables() {
        deleteIfExists("ia_i18n_audit_event");
        deleteIfExists("ia_i18n_context");
        deleteIfExists("ia_terminology_profile_term_snapshot");
        deleteIfExists("ia_terminology_profile");
        deleteIfExists("ia_translation_unit");
        deleteIfExists("ia_term_entry");
    }

    @Test
    void publishesNewTermIntoVersionedTerminologyProfileAndAuditsLifecycle() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-publish");

        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", published.profileVersion())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_code").value("CONTRACT_BASELINE"))
                .andExpect(jsonPath("$.profile_version").value(published.profileVersion()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM' && @.translations['en-US'] == 'Payment Term')]").isNotEmpty())
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM' && @.translations['es-ES'] == 'Término de pago')]").isNotEmpty());

        assertRowCount("ia_term_entry", "term_key = 'PAYMENT_TERM' and status = 'PUBLISHED' and version_no = 1", 1);
        assertRowCount("ia_translation_unit", "term_entry_id = '" + published.termEntryId() + "' and status = 'APPROVED'", 3);
        assertRowCount("ia_terminology_profile", "profile_code = 'CONTRACT_BASELINE' and profile_version = " + published.profileVersion() + " and status = 'PUBLISHED'", 1);
        assertAll(
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_CREATED' and target_id = '" + published.termEntryId() + "' and trace_id = 'trace-i18n-publish'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_SUBMITTED' and target_id = '" + published.termEntryId() + "' and trace_id = 'trace-i18n-publish'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_PUBLISHED' and target_id = '" + published.termEntryId() + "' and trace_id = 'trace-i18n-publish'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERMINOLOGY_PROFILE_CREATED' and target_id = 'CONTRACT_BASELINE' and profile_version = " + published.profileVersion() + " and trace_id = 'trace-i18n-publish'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERMINOLOGY_PROFILE_PUBLISHED' and target_id = 'CONTRACT_BASELINE' and profile_version = " + published.profileVersion() + " and trace_id = 'trace-i18n-publish'", 1));
    }

    @Test
    void reviewsTranslationUnitWithoutPublishingUnapprovedTerm() throws Exception {
        DraftTerm draft = createPaymentTerm("Payment Term", "Término de pago", "trace-i18n-review");
        submitTermForReview(draft.termEntryId(), "trace-i18n-review");

        mockMvc.perform(post("/api/intelligent-applications/i18n/translation-units/{translationUnitId}/approve", draft.enTranslationUnitId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewed_by\":\"reviewer-i18n\",\"trace_id\":\"trace-i18n-review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.language_code").value("en-US"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-review\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("I18N_TRANSLATION_REVIEW_INCOMPLETE"));

        assertRowCount("ia_term_entry", "term_entry_id = '" + draft.termEntryId() + "' and status = 'REVIEW'", 1);
        assertRowCount("ia_translation_unit", "translation_unit_id = '" + draft.enTranslationUnitId() + "' and status = 'APPROVED'", 1);
    }

    @Test
    void rollsBackPublishTermEntryWhenTerminologyProfileWriteFails() throws Exception {
        DraftTerm draft = createPaymentTerm("Payment Term", "Término de pago", "trace-i18n-publish-rollback");
        submitTermForReview(draft.termEntryId(), "trace-i18n-publish-rollback");
        for (String translationUnitId : new String[]{draft.zhTranslationUnitId(), draft.enTranslationUnitId(), draft.esTranslationUnitId()}) {
            approveTranslationUnit(translationUnitId, "trace-i18n-publish-rollback");
        }
        jdbcTemplate.update("""
                insert into ia_terminology_profile
                (profile_code, profile_version, domain_filter, language_scope_json, included_term_keys_json, snapshot_payload_json, published_at, status)
                values ('CONTRACT_BASELINE', 1, 'CONTRACT', '[]', '[]', '{}', current_timestamp, 'DEPRECATED')
                """);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-publish-rollback\"}")))
                .hasCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        assertAll(
                () -> assertRowCount("ia_term_entry", "term_entry_id = '" + draft.termEntryId() + "' and status = 'REVIEW' and version_no = 0", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_PUBLISHED' and target_id = '" + draft.termEntryId() + "' and trace_id = 'trace-i18n-publish-rollback'", 0),
                () -> assertRowCount("ia_terminology_profile", "profile_code = 'CONTRACT_BASELINE' and profile_version = 1 and status = 'PUBLISHED'", 0));
    }

    @Test
    void doesNotExposeProfileCacheWhenPublishRollsBackAfterSnapshotWrite() throws Exception {
        DraftTerm draft = createPaymentTerm("Payment Term", "Término de pago", "trace-i18n-cache-rollback");
        submitTermForReview(draft.termEntryId(), "trace-i18n-cache-rollback");
        for (String translationUnitId : new String[]{draft.zhTranslationUnitId(), draft.enTranslationUnitId(), draft.esTranslationUnitId()}) {
            approveTranslationUnit(translationUnitId, "trace-i18n-cache-rollback");
        }
        jdbcTemplate.execute("""
                alter table ia_i18n_audit_event
                add constraint ck_i18n_audit_reject_profile_published
                check (action <> 'TERMINOLOGY_PROFILE_PUBLISHED')
                """);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-cache-rollback\"}")))
                .hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertAll(
                () -> assertRowCount("ia_term_entry", "term_entry_id = '" + draft.termEntryId() + "' and status = 'REVIEW' and version_no = 0", 1),
                () -> assertRowCount("ia_terminology_profile", "profile_code = 'CONTRACT_BASELINE' and profile_version = 1", 0));
        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_source").value("DATABASE"))
                .andExpect(jsonPath("$.status").value("MISSING"))
                .andExpect(jsonPath("$.terms").isEmpty());
    }

    @Test
    void deprecatesTermAndExcludesItFromNewProfilesWithoutChangingHistoricalSnapshots() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-deprecate");
        String context = createContext("AI_JOB", "ai-job-deprecate", "CONTRACT_BASELINE", null, "trace-i18n-deprecate-context");
        String contextId = jsonString(context, "i18n_context_id");

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/deprecate", published.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-deprecate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"))
                .andExpect(jsonPath("$.new_profile_version").value(published.profileVersion() + 1));

        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", published.profileVersion() + 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM')]").isEmpty());
        mockMvc.perform(get("/api/intelligent-applications/i18n/contexts/{contextId}", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminology_profile_code").value("CONTRACT_BASELINE"))
                .andExpect(jsonPath("$.profile_version").value(published.profileVersion()))
                .andExpect(jsonPath("$.terminology_snapshot.terms[?(@.term_key == 'PAYMENT_TERM')]").isNotEmpty());
        assertAll(
                () -> assertRowCount("ia_translation_unit", "term_entry_id = '" + published.termEntryId() + "' and status = 'DEPRECATED'", 3),
                () -> assertRowCount("ia_terminology_profile", "profile_code = 'CONTRACT_BASELINE' and profile_version = " + published.profileVersion() + " and status = 'DEPRECATED'", 1),
                () -> assertRowCount("ia_terminology_profile", "profile_code = 'CONTRACT_BASELINE' and profile_version = " + (published.profileVersion() + 1) + " and status = 'PUBLISHED'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_DEPRECATED' and target_id = '" + published.termEntryId() + "' and trace_id = 'trace-i18n-deprecate'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TRANSLATION_UNIT_DEPRECATED' and target_type = 'TRANSLATION_UNIT' and trace_id = 'trace-i18n-deprecate'", 3),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERMINOLOGY_PROFILE_DEPRECATED' and target_id = 'CONTRACT_BASELINE' and profile_version = " + published.profileVersion() + " and trace_id = 'trace-i18n-deprecate'", 1));
    }

    @Test
    void deprecatesTermFromRequestedTerminologyProfileInsteadOfDefaultProfile() throws Exception {
        DraftTerm draft = createPaymentTerm("Payment Term", "Término de pago", "trace-i18n-domain-deprecate");
        submitTermForReview(draft.termEntryId(), "trace-i18n-domain-deprecate");
        for (String translationUnitId : new String[]{draft.zhTranslationUnitId(), draft.enTranslationUnitId(), draft.esTranslationUnitId()}) {
            mockMvc.perform(post("/api/intelligent-applications/i18n/translation-units/{translationUnitId}/approve", translationUnitId)
                            .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reviewed_by\":\"reviewer-i18n\",\"trace_id\":\"trace-i18n-domain-deprecate\"}"))
                    .andExpect(status().isOk());
        }
        String published = mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"LEGAL_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-domain-deprecate\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int legalVersion = intValue(published, "profile_version");

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/deprecate", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"LEGAL_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-domain-deprecate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_profile_code").value("LEGAL_BASELINE"))
                .andExpect(jsonPath("$.new_profile_version").value(legalVersion + 1));

        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "LEGAL_BASELINE", legalVersion + 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_code").value("LEGAL_BASELINE"))
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM')]").isEmpty());
    }

    @Test
    void rejectsDeprecatingMissingTermEntryWithoutAuditOrProfileSideEffects() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/deprecate", "term-missing-deprecate")
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-missing-deprecate\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_ENTRY_NOT_FOUND"));

        assertAll(
                () -> assertRowCount("ia_i18n_audit_event", "target_id = 'term-missing-deprecate' and trace_id = 'trace-i18n-missing-deprecate'", 0),
                () -> assertRowCount("ia_terminology_profile", "profile_code = 'CONTRACT_BASELINE'", 0));
    }

    @Test
    void rejectsRevisingMissingTermEntryWithoutTranslationUnitOrAuditSideEffects() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/translations/revise", "term-missing-revise")
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language_code\":\"en-US\",\"surface_form\":\"Ghost Term\",\"operator_id\":\"editor-i18n\",\"trace_id\":\"trace-i18n-missing-revise\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_ENTRY_NOT_FOUND"));

        assertAll(
                () -> assertRowCount("ia_translation_unit", "term_entry_id = 'term-missing-revise'", 0),
                () -> assertRowCount("ia_i18n_audit_event", "trace_id = 'trace-i18n-missing-revise'", 0));
    }

    @Test
    void rejectsRevisingDeprecatedTermEntryWithoutRevivingReviewState() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-deprecated-revise-base");
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/deprecate", published.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-deprecated-revise-deprecate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/translations/revise", published.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language_code\":\"en-US\",\"surface_form\":\"Revived Term\",\"operator_id\":\"editor-i18n\",\"trace_id\":\"trace-i18n-deprecated-revise\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_ENTRY_DEPRECATED"));

        assertAll(
                () -> assertRowCount("ia_term_entry", "term_entry_id = '" + published.termEntryId() + "' and status = 'DEPRECATED'", 1),
                () -> assertRowCount("ia_translation_unit", "term_entry_id = '" + published.termEntryId() + "' and surface_form = 'Revived Term'", 0),
                () -> assertRowCount("ia_i18n_audit_event", "trace_id = 'trace-i18n-deprecated-revise'", 0));
    }

    @Test
    void rejectsSubmittingDeprecatedTermForReviewWithoutRevivingStateOrAudit() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-deprecated-submit-base");
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/deprecate", published.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-deprecated-submit-deprecate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/submit-review", published.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"editor-i18n\",\"trace_id\":\"trace-i18n-deprecated-submit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_ENTRY_NOT_SUBMITTABLE"));

        assertAll(
                () -> assertRowCount("ia_term_entry", "term_entry_id = '" + published.termEntryId() + "' and status = 'DEPRECATED'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_SUBMITTED' and target_id = '" + published.termEntryId() + "' and trace_id = 'trace-i18n-deprecated-submit'", 0));
    }

    @Test
    void rejectsSubmittingPublishedTermForReviewWithoutStateOrAuditSideEffects() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-published-submit-base");

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/submit-review", published.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"editor-i18n\",\"trace_id\":\"trace-i18n-published-submit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_ENTRY_NOT_SUBMITTABLE"));

        assertAll(
                () -> assertRowCount("ia_term_entry", "term_entry_id = '" + published.termEntryId() + "' and status = 'PUBLISHED'", 1),
                () -> assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_SUBMITTED' and target_id = '" + published.termEntryId() + "' and trace_id = 'trace-i18n-published-submit'", 0));
    }

    @Test
    void preservesMixedLanguageSegmentsAndUsesFailedStatusForUnknownLanguageDegradation() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-mixed");

        mockMvc.perform(post("/api/intelligent-applications/i18n/contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"OCR_RESULT","owner_id":"ocr-mixed-1","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","display_label_language":"es-ES","language_segments":[{"segment_id":"s-zh","language_code":"zh-CN","text":"付款条件"},{"segment_id":"s-en","language_code":"en-US","text":"Payment Term"},{"segment_id":"s-es","language_code":"es-ES","text":"Término de pago"}],"trace_id":"trace-i18n-mixed"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.i18n_status").value("APPLIED"))
                .andExpect(jsonPath("$.input_language").value("MIXED"))
                .andExpect(jsonPath("$.normalized_language").value("en-US"))
                .andExpect(jsonPath("$.output_language").value("en-US"))
                .andExpect(jsonPath("$.display_label_language").value("es-ES"))
                .andExpect(jsonPath("$.profile_version").value(published.profileVersion()))
                .andExpect(jsonPath("$.segment_language_payload.segments[?(@.segment_id == 's-zh' && @.language_code == 'zh-CN')]").isNotEmpty())
                .andExpect(jsonPath("$.segment_language_payload.segments[?(@.segment_id == 's-es' && @.language_code == 'es-ES')]").isNotEmpty());

        mockMvc.perform(post("/api/intelligent-applications/i18n/contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"OCR_RESULT","owner_id":"ocr-unknown-1","terminology_profile_code":"CONTRACT_BASELINE","source_language":"UNKNOWN","response_language":"en-US","language_segments":[{"segment_id":"s-unknown","language_code":"UNKNOWN","text":"???"}],"trace_id":"trace-i18n-failed"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.i18n_status").value("FAILED"))
                .andExpect(jsonPath("$.downstream_degradation.degradation_action").value("BYPASS_TERMINOLOGY_NORMALIZATION"))
                .andExpect(jsonPath("$.terminology_snapshot.terms").isEmpty());
    }

    @Test
    void rejectsMissingTerminologyProfileInsteadOfApplyingEmptySnapshot() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"AI_JOB","owner_id":"ai-job-missing-profile","terminology_profile_code":"MISSING_PROFILE","source_language":"zh-CN","response_language":"en-US","language_segments":[{"segment_id":"s1","language_code":"zh-CN","text":"付款条件"}],"trace_id":"trace-i18n-missing-profile"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED"));
    }

    @Test
    void rejectsExplicitMissingTerminologyProfileEvenWhenLanguageDetectionFailed() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"AI_JOB","owner_id":"ai-job-unknown-missing-profile","terminology_profile_code":"MISSING_PROFILE","source_language":"UNKNOWN","response_language":"en-US","language_segments":[{"segment_id":"s-unknown","language_code":"UNKNOWN","text":"???"}],"trace_id":"trace-i18n-unknown-missing-profile"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED"));

        assertRowCount("ia_i18n_context", "owner_id = 'ai-job-unknown-missing-profile'", 0);
    }

    @Test
    void fallsBackToOfficialDatabaseSnapshotWithoutTestOnlyCacheMutation() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-cache");
        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", published.profileVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_source").value("CACHE"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}/cache/evict", "CONTRACT_BASELINE", published.profileVersion())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"ops-i18n\",\"trace_id\":\"trace-i18n-cache-evict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_evict_status").value("EVICTED"));

        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", published.profileVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_source").value("DATABASE"))
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM')]").isNotEmpty());

        reviseEnglishTranslation(published.termEntryId(), "Settlement Term", "trace-i18n-cache-v2");

        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", published.profileVersion() + 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_source").value("CACHE"))
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM' && @.translations['en-US'] == 'Settlement Term')]").isNotEmpty());

        assertRowCount("ia_i18n_audit_event", "action = 'TERMINOLOGY_PROFILE_CACHE_EVICTED' and target_id = 'CONTRACT_BASELINE' and profile_version = " + published.profileVersion(), 1);
    }

    @Test
    void reloadsDeprecatedHistoricalTerminologyProfileFromDatabaseAfterCacheEviction() throws Exception {
        PublishedTerm v1 = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-history-db-v1");
        reviseEnglishTranslation(v1.termEntryId(), "Settlement Term", "trace-i18n-history-db-v2");

        mockMvc.perform(post("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}/cache/evict", "CONTRACT_BASELINE", v1.profileVersion())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"ops-i18n\",\"trace_id\":\"trace-i18n-history-db-evict\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}", "CONTRACT_BASELINE", v1.profileVersion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_source").value("DATABASE"))
                .andExpect(jsonPath("$.status").value("DEPRECATED"))
                .andExpect(jsonPath("$.terms[?(@.term_key == 'PAYMENT_TERM' && @.translations['en-US'] == 'Payment Term')]").isNotEmpty());
    }

    @Test
    void rejectsI18nGovernanceMutationsAndCacheEvictionWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"term_key":"UNAUTHORIZED_TERM","domain":"CONTRACT","canonical_language":"zh-CN","created_by":"editor-i18n","translations":[{"language_code":"zh-CN","surface_form":"未授权术语"},{"language_code":"en-US","surface_form":"Unauthorized Term"},{"language_code":"es-ES","surface_form":"Término no autorizado"}],"trace_id":"trace-i18n-permission-create-denied"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("I18N_GOVERNANCE_PERMISSION_DENIED"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms")
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"term_key":"AUTHORIZED_TERM","domain":"CONTRACT","canonical_language":"zh-CN","created_by":"editor-i18n","translations":[{"language_code":"zh-CN","surface_form":"授权术语"},{"language_code":"en-US","surface_form":"Authorized Term"},{"language_code":"es-ES","surface_form":"Término autorizado"}],"trace_id":"trace-i18n-permission-create-allowed"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertRowCount("ia_term_entry", "term_key = 'AUTHORIZED_TERM' and status = 'DRAFT'", 1);

        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-permission-cache");
        mockMvc.perform(post("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}/cache/evict", "CONTRACT_BASELINE", published.profileVersion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"ops-i18n\",\"trace_id\":\"trace-i18n-permission-cache-denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("I18N_GOVERNANCE_PERMISSION_DENIED"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}/cache/evict", "CONTRACT_BASELINE", published.profileVersion())
                        .header("X-CMP-Permissions", "NO_I18N_GOVERNANCE_MANAGE, I18N_GOVERNANCE_MANAGE_DISABLED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"ops-i18n\",\"trace_id\":\"trace-i18n-permission-cache-near-match\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("I18N_GOVERNANCE_PERMISSION_DENIED"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/profiles/{profileCode}/versions/{profileVersion}/cache/evict", "CONTRACT_BASELINE", published.profileVersion())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"ops-i18n\",\"trace_id\":\"trace-i18n-permission-cache-allowed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cache_evict_status").value("EVICTED"));
    }

    @Test
    void rejectsDraftTermApprovalAndPublishUntilSubmittedForReview() throws Exception {
        DraftTerm draft = createPaymentTerm("Payment Term", "Término de pago", "trace-i18n-state-machine");

        mockMvc.perform(post("/api/intelligent-applications/i18n/translation-units/{translationUnitId}/approve", draft.enTranslationUnitId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewed_by\":\"reviewer-i18n\",\"trace_id\":\"trace-i18n-state-machine-approve-draft\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_NOT_IN_REVIEW"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-state-machine-publish-draft\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("I18N_TERM_NOT_IN_REVIEW"));

        submitTermForReview(draft.termEntryId(), "trace-i18n-state-machine-review");
        for (String translationUnitId : new String[]{draft.zhTranslationUnitId(), draft.enTranslationUnitId(), draft.esTranslationUnitId()}) {
            approveTranslationUnit(translationUnitId, "trace-i18n-state-machine-review");
        }
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"trace-i18n-state-machine-review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void doesNotWriteSubmitReviewAuditForMissingTermEntry() throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/submit-review", "term-missing")
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"editor-i18n\",\"trace_id\":\"trace-i18n-missing-submit\"}"))
                .andExpect(status().isBadRequest());

        assertRowCount("ia_i18n_audit_event", "action = 'TERM_ENTRY_SUBMITTED' and target_id = 'term-missing' and trace_id = 'trace-i18n-missing-submit'", 0);
    }

    @Test
    void mainChainRejectsMissingTerminologyProfileInsteadOfSilentlyDroppingI18nContext() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-main-chain-missing-term");
        ContractDocument sample = createContractDocument("多语言缺失术语合同", "dept-i18n-a", "trace-i18n-main-chain-missing");

        mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-i18n-missing-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"AI_CONTEXT_INPUT","terminology_profile_code":"MISSING_PROFILE","source_language":"zh-CN","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-missing-ocr"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED"));
        assertRowCount("ia_ocr_job", "trace_id = 'trace-i18n-main-chain-missing-ocr'", 0);
        assertRowCount("ia_ocr_result_aggregate", "document_version_id = '" + sample.documentVersionId() + "'", 0);

        String ocr = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-i18n-valid-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"AI_CONTEXT_INPUT","terminology_profile_code":"CONTRACT_BASELINE","source_language":"zh-CN","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-valid-ocr"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.i18n_context.profile_version").value(published.profileVersion()))
                .andReturn().getResponse().getContentAsString();
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");

        mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"zh-CN","response_language":"en-US","trace_id":"trace-i18n-main-chain-valid-search"}
                                """.formatted(sample.contractId(), sample.documentVersionId(), ocrResultId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-i18n-a")
                        .header("Idempotency-Key", "idem-ai-i18n-missing-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"MISSING_PROFILE","source_language":"zh-CN","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-missing-ai"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED"));

        mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-i18n-a")
                        .header("Idempotency-Key", "idem-rank-i18n-missing-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"MISSING_PROFILE","source_language":"zh-CN","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-missing-rank"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED"));
        assertRowCount("ia_i18n_context", "owner_type = 'CANDIDATE_RANKING'", 0);
    }

    @Test
    void mainChainRejectsExplicitMissingTerminologyProfileBeforeUnknownLanguageDegradationSideEffects() throws Exception {
        ContractDocument sample = createContractDocument("未知语言缺失术语合同", "dept-i18n-a", "trace-i18n-main-chain-unknown-missing");

        mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-i18n-unknown-missing-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"AI_CONTEXT_INPUT","terminology_profile_code":"MISSING_PROFILE","source_language":"UNKNOWN","response_language":"en-US","language_segments":[{"segment_id":"s-unknown","language_code":"UNKNOWN","text":"???"}],"actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-unknown-missing-ocr"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED"));

        assertAll(
                () -> assertRowCount("ia_ocr_job", "trace_id = 'trace-i18n-main-chain-unknown-missing-ocr'", 0),
                () -> assertRowCount("ia_ocr_result_aggregate", "document_version_id = '" + sample.documentVersionId() + "'", 0),
                () -> assertRowCount("ia_i18n_context", "terminology_profile_code = 'MISSING_PROFILE'", 0));
    }

    @Test
    void aiApplicationValidatesExplicitMissingTerminologyProfileBeforeNoEvidenceProtectedRejection() throws Exception {
        ContractDocument sample = createContractDocument("多语言缺失术语前置拒绝合同", "dept-i18n-a", "trace-i18n-ai-precheck");

        String response = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-i18n-a")
                        .header("Idempotency-Key", "idem-ai-i18n-missing-profile-no-evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"MISSING_PROFILE","source_language":"zh-CN","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-ai-missing-profile-no-evidence"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andReturn().getResponse().getContentAsString();

        assertAll(
                () -> assertThat(response).contains("\"error_code\":\"I18N_TERMINOLOGY_PROFILE_NOT_PUBLISHED\""),
                () -> assertRowCount("ia_protected_result_snapshot", "guardrail_failure_code = 'AI_CONTEXT_NO_EVIDENCE'", 0),
                () -> assertRowCount("ia_ai_audit_event", "trace_id = 'trace-i18n-ai-missing-profile-no-evidence'", 0),
                () -> assertRowCount("ia_ai_application_job", "trace_id = 'trace-i18n-ai-missing-profile-no-evidence'", 0),
                () -> assertRowCount("ia_i18n_context", "owner_type = 'AI_JOB'", 0));
    }

    @Test
    void mainIntelligentApplicationChainAnchorsPublishedTerminologyContext() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-main-chain-term");
        ContractDocument sample = createContractDocument("多语言主链合同", "dept-i18n-a", "trace-i18n-main-chain");

        String ocr = mockMvc.perform(post("/api/intelligent-applications/ocr/jobs")
                        .header("X-CMP-Permissions", "CONTRACT_VIEW,OCR_CREATE")
                        .header("Idempotency-Key", "idem-ocr-i18n-main-chain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document_version_id":"%s","job_purpose":"AI_CONTEXT_INPUT","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","display_label_language":"zh-CN","language_hints":["zh-CN","en-US"],"actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-ocr"}
                                """.formatted(sample.documentVersionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.i18n_context.i18n_status").value("APPLIED"))
                .andExpect(jsonPath("$.i18n_context.terminology_profile_code").value("CONTRACT_BASELINE"))
                .andExpect(jsonPath("$.i18n_context.profile_version").value(published.profileVersion()))
                .andReturn().getResponse().getContentAsString();
        String ocrResultId = jsonString(ocr, "ocr_result_aggregate_id");

        String search = mockMvc.perform(post("/api/intelligent-applications/search/sources/refresh")
                        .header("X-CMP-Permissions", "SEARCH_INDEX_MANAGE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_types":["CONTRACT","DOCUMENT","OCR","CLAUSE"],"contract_id":"%s","document_version_id":"%s","ocr_result_aggregate_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","trace_id":"trace-i18n-main-chain-search"}
                                """.formatted(sample.contractId(), sample.documentVersionId(), ocrResultId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.i18n_context.i18n_status").value("APPLIED"))
                .andExpect(jsonPath("$.search_documents[0].i18n_context_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String ai = mockMvc.perform(post("/api/intelligent-applications/ai/jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-i18n-a")
                        .header("Idempotency-Key", "idem-ai-i18n-main-chain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-ai"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.i18n_context.i18n_status").value("APPLIED"))
                .andExpect(jsonPath("$.context_envelope.i18n_context_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String ranking = mockMvc.perform(post("/api/intelligent-applications/candidates/ranking-jobs")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW,SEARCH_QUERY")
                        .header("X-CMP-Org-Scope", "dept-i18n-a")
                        .header("Idempotency-Key", "idem-rank-i18n-main-chain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"application_type":"SUMMARY","contract_id":"%s","document_version_id":"%s","terminology_profile_code":"CONTRACT_BASELINE","source_language":"MIXED","response_language":"en-US","actor_id":"u-i18n","trace_id":"trace-i18n-main-chain-rank"}
                                """.formatted(sample.contractId(), sample.documentVersionId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.i18n_context.i18n_status").value("APPLIED"))
                .andExpect(jsonPath("$.ranking_snapshot.i18n_context_id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/intelligent-applications/candidates/writeback-candidates")
                        .header("X-CMP-Permissions", "AI_APPLICATION_CREATE,CONTRACT_VIEW")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result_id":"%s","trace_id":"trace-i18n-main-chain-writeback"}
                                """.formatted(jsonString(ranking, "result_id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.i18n_context_id").isNotEmpty())
                .andExpect(jsonPath("$.terminology_profile_code").value("CONTRACT_BASELINE"));

        assertThat(search).contains("i18n_context_id");
        assertThat(ai).contains("i18n_context_id");
    }

    @Test
    void blocksAiOutputWhenTerminologyIsMissingOrTranslatedInconsistently() throws Exception {
        PublishedTerm published = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-guardrail");
        String context = createContext("AI_JOB", "ai-job-guardrail", "CONTRACT_BASELINE", published.profileVersion(), "trace-i18n-guardrail-context");
        String contextId = jsonString(context, "i18n_context_id");

        mockMvc.perform(post("/api/intelligent-applications/i18n/guardrails/validate-output")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"i18n_context_id":"%s","output_language":"en-US","output_terms":[{"term_key":"PAYMENT_TERM","surface_form":"Agreement"}],"trace_id":"trace-i18n-guardrail-bad-translation"}
                                """.formatted(contextId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.guardrail_decision").value("BLOCK"))
                .andExpect(jsonPath("$.failure_code").value("I18N_TERM_TRANSLATION_MISMATCH"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/guardrails/validate-output")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"i18n_context_id":"%s","output_language":"en-US","output_terms":[{"term_key":"UNKNOWN_TERM","surface_form":"Unknown"}],"trace_id":"trace-i18n-guardrail-missing"}
                                """.formatted(contextId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.guardrail_decision").value("BLOCK"))
                .andExpect(jsonPath("$.failure_code").value("I18N_TERM_NOT_IN_SNAPSHOT"));

        assertRowCount("ia_i18n_audit_event", "action = 'I18N_OUTPUT_GUARDRAIL_BLOCKED' and target_id = '" + contextId + "'", 2);
    }

    @Test
    void keepsHistoricalTaskTerminologySnapshotImmutableAfterNewProfilePublication() throws Exception {
        PublishedTerm v1 = publishPaymentTerm("Payment Term", "Payment Term", "Término de pago", "trace-i18n-v1");
        String context = createContext("AI_JOB", "ai-job-history", "CONTRACT_BASELINE", v1.profileVersion(), "trace-i18n-history-context");
        String contextId = jsonString(context, "i18n_context_id");
        reviseEnglishTranslation(v1.termEntryId(), "Settlement Term", "trace-i18n-v2");

        mockMvc.perform(get("/api/intelligent-applications/i18n/contexts/{contextId}", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_version").value(v1.profileVersion()))
                .andExpect(jsonPath("$.terminology_snapshot.terms[?(@.term_key == 'PAYMENT_TERM' && @.translations['en-US'] == 'Payment Term')]").isNotEmpty());

        mockMvc.perform(post("/api/intelligent-applications/i18n/guardrails/validate-output")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"i18n_context_id":"%s","output_language":"en-US","output_terms":[{"term_key":"PAYMENT_TERM","surface_form":"Payment Term"}],"trace_id":"trace-i18n-history-ok"}
                                """.formatted(contextId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardrail_decision").value("PASS"));

        mockMvc.perform(post("/api/intelligent-applications/i18n/guardrails/validate-output")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"i18n_context_id":"%s","output_language":"en-US","output_terms":[{"term_key":"PAYMENT_TERM","surface_form":"Settlement Term"}],"trace_id":"trace-i18n-history-drift"}
                                """.formatted(contextId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failure_code").value("I18N_TERM_TRANSLATION_MISMATCH"));
    }

    private PublishedTerm publishPaymentTerm(String canonicalSurface, String englishSurface, String spanishSurface, String traceId) throws Exception {
        DraftTerm draft = createPaymentTerm(englishSurface, spanishSurface, traceId);
        submitTermForReview(draft.termEntryId(), traceId);
        for (String translationUnitId : new String[]{draft.zhTranslationUnitId(), draft.enTranslationUnitId(), draft.esTranslationUnitId()}) {
            approveTranslationUnit(translationUnitId, traceId);
        }
        String published = mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", draft.termEntryId())
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"" + traceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.terminology_profile.status").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(canonicalSurface).isNotBlank();
        return new PublishedTerm(draft.termEntryId(), intValue(published, "profile_version"));
    }

    private DraftTerm createPaymentTerm(String englishSurface, String spanishSurface, String traceId) throws Exception {
        String created = mockMvc.perform(post("/api/intelligent-applications/i18n/terms")
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"term_key":"PAYMENT_TERM","domain":"CONTRACT","canonical_language":"zh-CN","created_by":"editor-i18n","translations":[{"language_code":"zh-CN","surface_form":"付款条件"},{"language_code":"en-US","surface_form":"%s"},{"language_code":"es-ES","surface_form":"%s"}],"trace_id":"%s"}
                                """.formatted(englishSurface, spanishSurface, traceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return new DraftTerm(jsonString(created, "term_entry_id"), jsonString(created, "zh_translation_unit_id"), jsonString(created, "en_translation_unit_id"), jsonString(created, "es_translation_unit_id"));
    }

    private void submitTermForReview(String termEntryId, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/submit-review", termEntryId)
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator_id\":\"editor-i18n\",\"trace_id\":\"" + traceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW"));
    }

    private void approveTranslationUnit(String translationUnitId, String traceId) throws Exception {
        mockMvc.perform(post("/api/intelligent-applications/i18n/translation-units/{translationUnitId}/approve", translationUnitId)
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewed_by\":\"reviewer-i18n\",\"trace_id\":\"" + traceId + "\"}"))
                .andExpect(status().isOk());
    }

    private String createContext(String ownerType, String ownerId, String profileCode, Integer profileVersion, String traceId) throws Exception {
        String versionPart = profileVersion == null ? "" : ",\"profile_version\":" + profileVersion;
        return mockMvc.perform(post("/api/intelligent-applications/i18n/contexts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"%s","owner_id":"%s","terminology_profile_code":"%s"%s,"source_language":"zh-CN","response_language":"en-US","display_label_language":"zh-CN","language_segments":[{"segment_id":"s1","language_code":"zh-CN","text":"付款条件"}],"trace_id":"%s"}
                                """.formatted(ownerType, ownerId, profileCode, versionPart, traceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.i18n_status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
    }

    private void reviseEnglishTranslation(String termEntryId, String newSurfaceForm, String traceId) throws Exception {
        String revised = mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/translations/revise", termEntryId)
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language_code":"en-US","surface_form":"%s","operator_id":"editor-i18n","trace_id":"%s"}
                                """.formatted(newSurfaceForm, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String translationUnitId = jsonString(revised, "translation_unit_id");
        approveTranslationUnit(translationUnitId, traceId);
        mockMvc.perform(post("/api/intelligent-applications/i18n/terms/{termEntryId}/publish", termEntryId)
                        .header("X-CMP-Permissions", I18N_GOVERNANCE_PERMISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile_code\":\"CONTRACT_BASELINE\",\"operator_id\":\"publisher-i18n\",\"trace_id\":\"" + traceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminology_profile.profile_version").value(2));
    }

    private String jsonString(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return json.substring(valueStart, valueEnd);
    }

    private int intValue(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int start = json.indexOf(marker);
        assertThat(start).as("响应缺少字段: %s, body=%s", fieldName, json).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        return Integer.parseInt(json.substring(valueStart, valueEnd));
    }

    private void assertRowCount(String tableName, String whereClause, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName + " where " + whereClause, Integer.class);
        assertThat(count).isEqualTo(expectedCount);
    }

    private void deleteIfExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, tableName.toUpperCase());
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM " + tableName);
        }
    }

    private record DraftTerm(String termEntryId, String zhTranslationUnitId, String enTranslationUnitId, String esTranslationUnitId) {
    }

    private record PublishedTerm(String termEntryId, int profileVersion) {
    }

    private ContractDocument createContractDocument(String contractName, String ownerOrgUnitId, String traceId) throws Exception {
        String contract = mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contract_name":"%s","owner_user_id":"u-i18n-owner","owner_org_unit_id":"%s","category_code":"I18N","category_name":"多语言合同","trace_id":"%s"}
                                """.formatted(contractName, ownerOrgUnitId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String contractId = jsonString(contract, "contract_id");
        String document = mockMvc.perform(post("/api/document-center/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"owner_type":"CONTRACT","owner_id":"%s","document_role":"MAIN_BODY","document_title":"%s.pdf","file_upload_token":"%s-token","trace_id":"%s-doc"}
                                """.formatted(contractId, contractName, traceId, traceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new ContractDocument(contractId, jsonString(document, "document_asset_id"), jsonString(document, "document_version_id"));
    }

    private record ContractDocument(String contractId, String documentAssetId, String documentVersionId) {
    }
}
