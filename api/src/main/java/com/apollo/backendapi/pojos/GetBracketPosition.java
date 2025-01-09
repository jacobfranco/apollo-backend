package com.apollo.backendapi.pojos;

import com.apollo.backend.data.BracketPosition;

public class GetBracketPosition {
    public String part;
    public int col;
    public int offset;

    public GetBracketPosition(BracketPosition bp) {
        this.part = bp.getPart();
        this.col = bp.getCol();
        this.offset = bp.getOffset();
    }
}