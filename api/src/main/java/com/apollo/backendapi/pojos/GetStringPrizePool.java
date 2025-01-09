package com.apollo.backendapi.pojos;

import com.apollo.backend.data.StringPrizePool;

public class GetStringPrizePool {
    public String total;
    public String first;
    public String second;
    public String third;

    public GetStringPrizePool(StringPrizePool prizePool) {
        this.total = prizePool.getTotal();
        this.first = prizePool.getFirst();
        this.second = prizePool.getSecond();
        this.third = prizePool.getThird();
    }
}
