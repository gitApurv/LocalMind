package com.localmind.chat.controller;

import com.localmind.chat.dto.ChatEvent;
import com.localmind.chat.dto.ChatRequest;
import com.localmind.chat.service.ChatOrchestratorService;
import com.localmind.common.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> streamChat(@CurrentUserId String userId,
            @Valid @RequestBody ChatRequest request) {
        return chatOrchestratorService.streamChat(request, userId).map(event -> ServerSentEvent.builder(event).build());
    }
}
