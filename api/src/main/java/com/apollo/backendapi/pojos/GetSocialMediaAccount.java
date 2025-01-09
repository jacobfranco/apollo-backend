package com.apollo.backendapi.pojos;

import com.apollo.backend.data.SocialMediaAccount;

public class GetSocialMediaAccount {
    public String handle;
    public String url;
    public GetSocialMediaPlatform platform;

    public GetSocialMediaAccount(SocialMediaAccount account) {
        this.handle = account.getHandle();
        this.url = account.getUrl();
        this.platform = new GetSocialMediaPlatform(account.getPlatform());
    }
}