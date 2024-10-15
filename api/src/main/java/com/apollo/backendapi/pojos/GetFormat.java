package com.apollo.backendapi.pojos;

import com.apollo.backend.data.Format;

public class GetFormat {
    public int bestOf;

    public GetFormat(Format format) {
        this.bestOf = format.getBestOf();
    }

    public GetFormat(int bestOf) {
        this.bestOf = bestOf;
    }
}
