package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.AddAvailabilityRequest;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.RemoveAvailabilityRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 可用时段维护。
 */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public AvailabilitySlot add(@RequestBody AddAvailabilityRequest request) {
        return availabilityService.addSlot(request.operatorId(), request.operatorRole(),
                request.ownerId(), request.start(), request.end());
    }

    @DeleteMapping("/{slotId}")
    public Map<String, Object> remove(@PathVariable String slotId,
                                      @RequestBody RemoveAvailabilityRequest request) {
        availabilityService.removeSlot(request.operatorId(), request.operatorRole(), slotId);
        return Map.of("removed", slotId);
    }

    @GetMapping("/{ownerId}")
    public List<AvailabilitySlot> list(@PathVariable String ownerId) {
        return availabilityService.slotsOf(ownerId);
    }
}
