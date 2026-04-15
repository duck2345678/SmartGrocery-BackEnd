package com.smartgrocery.backend.exception;

import lombok.Getter;

@Getter
public class ResourceOwnershipException extends RuntimeException {

    private final Long actorUserId;
    private final Long targetUserId;
    private final String resourceType;
    private final Long resourceId;

    public ResourceOwnershipException(
            Long actorUserId,
            Long targetUserId,
            String resourceType,
            Long resourceId
    ) {
        super("Forbidden");
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}
