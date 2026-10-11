package com.onlinejudge.backend.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.QueueStatusResponse;
import com.onlinejudge.backend.service.QueueStatusService;

/** Admin system endpoints (PRD §7.7). */
@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final QueueStatusService queueStatusService;

    public AdminSystemController(QueueStatusService queueStatusService) {
        this.queueStatusService = queueStatusService;
    }

    @GetMapping("/queue-status")
    public QueueStatusResponse queueStatus() {
        return queueStatusService.current();
    }
}
