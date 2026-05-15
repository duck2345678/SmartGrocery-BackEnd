package com.smartgrocery.backend.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class Pass2EventListener {

    private final AiOrchestrationService aiOrchestrationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPass2Requested(Pass2RequestedEvent event) {
        aiOrchestrationService.orchestrate(event.aiMessageId(), event.userId(), event.userMessage());
    }

}
