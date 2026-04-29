package com.cmp.platform.foundations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PlatformJobController {

    private final PlatformJobService service;

    PlatformJobController(PlatformJobService service) {
        this.service = service;
    }

    @PostMapping("/api/platform/jobs")
    ResponseEntity<Map<String, Object>> createJob(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.createJob(request));
    }

    @GetMapping("/api/platform/jobs/{platformJobId}")
    Map<String, Object> job(@PathVariable String platformJobId) {
        return service.job(platformJobId);
    }

    @GetMapping("/api/platform/jobs")
    Map<String, Object> jobs(@RequestParam(required = false) String consumer_module,
                             @RequestParam(required = false) String source_module,
                             @RequestParam(required = false) String job_status) {
        return service.jobs(consumer_module, source_module, job_status);
    }
}

@org.springframework.stereotype.Service
class PlatformJobService {

    private final JdbcTemplate jdbcTemplate;

    PlatformJobService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    Map<String, Object> createJob(Map<String, Object> request) {
        String platformJobId = "platform-job-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into platform_job
                (platform_job_id, job_type, job_status, source_module, consumer_module, resource_type, resource_id,
                 business_object_type, business_object_id, priority, attempt_no, max_attempts, runner_code, trace_id, created_at, updated_at)
                values (?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                """, platformJobId, text(request, "job_type", "GENERIC_ASYNC_JOB"), text(request, "source_module", "platform"),
                text(request, "consumer_module", "platform"), text(request, "resource_type", "OBJECT"), text(request, "resource_id", null),
                text(request, "business_object_type", null), text(request, "business_object_id", null), number(request, "priority", 50),
                number(request, "max_attempts", 3), text(request, "runner_code", "platform-job-runner"), text(request, "trace_id", "trace-" + UUID.randomUUID()),
                Timestamp.from(now), Timestamp.from(now));
        return job(platformJobId);
    }

    Map<String, Object> job(String platformJobId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select platform_job_id, job_type, job_status, source_module, consumer_module, resource_type, resource_id,
                           business_object_type, business_object_id, priority, attempt_no, max_attempts, runner_code, trace_id, created_at, updated_at
                    from platform_job where platform_job_id = ?
                    """, (rs, rowNum) -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("platform_job_id", rs.getString("platform_job_id"));
                body.put("job_type", rs.getString("job_type"));
                body.put("job_status", rs.getString("job_status"));
                body.put("source_module", rs.getString("source_module"));
                body.put("consumer_module", rs.getString("consumer_module"));
                body.put("resource_type", rs.getString("resource_type"));
                body.put("resource_id", rs.getString("resource_id"));
                body.put("business_object_type", rs.getString("business_object_type"));
                body.put("business_object_id", rs.getString("business_object_id"));
                body.put("priority", rs.getInt("priority"));
                body.put("attempt_no", rs.getInt("attempt_no"));
                body.put("max_attempts", rs.getInt("max_attempts"));
                body.put("runner_code", rs.getString("runner_code"));
                body.put("trace_id", rs.getString("trace_id"));
                body.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
                body.put("updated_at", rs.getTimestamp("updated_at").toInstant().toString());
                return body;
            }, platformJobId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("platform_job 不存在: " + platformJobId);
        }
    }

    Map<String, Object> jobs(String consumerModule, String sourceModule, String jobStatus) {
        List<Map<String, Object>> items = jdbcTemplate.query("""
                select platform_job_id
                from platform_job
                where (? is null or consumer_module = ?)
                  and (? is null or source_module = ?)
                  and (? is null or job_status = ?)
                order by created_at, platform_job_id
                """, (rs, rowNum) -> job(rs.getString("platform_job_id")), consumerModule, consumerModule, sourceModule, sourceModule, jobStatus, jobStatus);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        return body;
    }

    private String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private int number(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
