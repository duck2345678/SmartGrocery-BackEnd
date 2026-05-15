package com.smartgrocery.backend.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class Pass2EventListener {

    private final AiPass2StreamService aiPass2StreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPass2Requested(Pass2RequestedEvent event) {
        aiPass2StreamService.submitPass2Job(event.aiMessageId(), event.userId());
    }
}
