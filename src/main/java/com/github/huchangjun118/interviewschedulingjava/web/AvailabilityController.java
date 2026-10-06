package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import com.github.huchangjun118.interviewschedulingjava.dto.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService;
import com.github.huchangjun118.interviewschedulingjava.service.PersistenceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可用时段维护接口。入参可带任意时区偏移，服务端统一换算到 UTC。
 */
@RestController
@RequestMapping("/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final PersistenceService persistenceService;

    public AvailabilityController(AvailabilityService availabilityService,
                                  PersistenceService persistenceService) {
        this.availabilityService = availabilityService;
        this.persistenceService = persistenceService;
    }

    @PostMapping("/{ownerId}")
    public AvailabilitySlot add(@PathVariable String ownerId, @RequestBody TimeWindow window) {
        AvailabilitySlot slot = availabilityService.addSlot(ownerId, window.startUtc(), window.endUtc());
        persistenceService.snapshot();
        return slot;
    }

    @GetMapping("/{ownerId}")
    public List<AvailabilitySlot> list(@PathVariable String ownerId) {
        return availabilityService.slotsOf(ownerId);
    }

    @DeleteMapping("/{ownerId}")
    public void remove(@PathVariable String ownerId, @RequestBody TimeWindow window) {
        availabilityService.removeSlot(ownerId, window.startUtc(), window.endUtc());
        persistenceService.snapshot();
    }
}
