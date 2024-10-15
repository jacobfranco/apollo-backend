package com.apollo.backendapi.pojos;

import com.apollo.backend.data.TournamentDefaults;

public class GetTournamentDefaults {
    public GetGameVersion gameVersion;

    public GetTournamentDefaults(TournamentDefaults defaults) {
        this.gameVersion = defaults.isSetGameVersion() ? new GetGameVersion(defaults.getGameVersion()) : null;
    }
}
