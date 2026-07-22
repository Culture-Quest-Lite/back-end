package org.sep490.backend.module.exploration.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.dto.filter.RouteParticipantFilter;
import org.sep490.backend.module.exploration.dto.request.StartGroupQuestRoute;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantDetailResponse;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantResponse;
import org.sep490.backend.module.exploration.service.inter.RouteParticipantService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/route-participants")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteParticipantController {

    RouteParticipantService routeParticipantService;

    @PostMapping("/start/{id}")
    public ResponseEntity<RouteParticipantResponse> start(@PathVariable("id") Long routeId) {
        HashMap<Integer, RouteParticipantResponse> response = routeParticipantService.startRouteProgress(routeId);
        return response.containsKey(201)
                ? ResponseEntity.status(HttpStatus.CREATED).body(response.get(201))
                : ResponseEntity.ok(response.get(200));
    }

    @PutMapping("/abandon/{id}")
    public ResponseEntity<RouteParticipantResponse> abandon(@PathVariable("id") Long routeId) {
        RouteParticipantResponse response = routeParticipantService.abandonRouteProgress(routeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<RouteParticipantResponse>> getAll(@ModelAttribute RouteParticipantFilter filter) {
        Page<RouteParticipantResponse> response = routeParticipantService.getAll(filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteParticipantDetailResponse> getById(@PathVariable("id") Long routeParticipantId) {
        RouteParticipantDetailResponse response = routeParticipantService.getRouteProgress(routeParticipantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/join/{token}")
    public ResponseEntity<RouteParticipantResponse> join(@PathVariable("token") String token) {
        HashMap<Integer, RouteParticipantResponse> response = routeParticipantService.joinRouteFromLink(token);
        return response.containsKey(201)
                ? ResponseEntity.status(HttpStatus.CREATED).body(response.get(201))
                : ResponseEntity.ok(response.get(200));
    }

    @PostMapping("/join/group-quest")
    public ResponseEntity<Void> startGroupQuest(@RequestBody StartGroupQuestRoute request) {
        routeParticipantService.startGroupQuest(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
