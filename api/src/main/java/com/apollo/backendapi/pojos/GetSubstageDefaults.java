package com.apollo.backendapi.pojos;

import com.apollo.backend.data.SubstageDefaults;

public class GetSubstageDefaults {
    public GetGameVersion gameVersion;
    public GetFormat seriesFormat;

    public GetSubstageDefaults(SubstageDefaults defaults) {
        this.gameVersion = defaults.isSetGameVersion() ? new GetGameVersion(defaults.getGameVersion()) : null;
        this.seriesFormat = defaults.isSetSeriesFormat() ? new GetFormat(defaults.getSeriesFormat()) : null;
    }
}
