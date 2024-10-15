package com.apollo.backendapi.pojos;

import com.apollo.backend.data.TournamentLinks;

public class GetTournamentLinks {
    public String website;
    public String wiki;

    public GetTournamentLinks(TournamentLinks links) {
        this.website = links.getWebsite();
        this.wiki = links.getWiki();
    }
}
