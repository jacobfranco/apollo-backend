package com.apollo.backendapi.pojos;

import com.apollo.backend.data.TournamentCopy;

public class GetTournamentCopy {
    public String generalDescription;
    public String shortDescription;
    public String formatDescription;

    public GetTournamentCopy(TournamentCopy copy) {
        this.generalDescription = copy.getGeneralDescription();
        this.shortDescription = copy.getShortDescription();
        this.formatDescription = copy.getFormatDescription();
    }
}
