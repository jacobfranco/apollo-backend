package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Movement;

public class GetMovement {
    public int position;
    public int substageId;
    public String type;

    public GetMovement(Movement movement) {
        this.position = movement.getPosition();
        this.substageId = movement.getSubstageId();
        this.type = movement.getType();
    }
}
