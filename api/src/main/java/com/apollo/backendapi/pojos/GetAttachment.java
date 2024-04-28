package com.apollo.backendapi.pojos;

import com.apollo.backend.ApolloConfig;
import com.apollo.backend.data.*;
import com.apollo.backendapi.ApolloApiConfig;

import java.util.Map;

public class GetAttachment {
    public String id;
    public String type; // unknown, image, gifv, video, audio
    public String url;
    public String preview_url;
    public String remote_url; // nullable
    public Map meta;
    public String description; // nullable
    public String blurhash;

    public GetAttachment() { }

    public GetAttachment(AttachmentWithId attachmentWithId) {
        Attachment attachment = attachmentWithId.attachment;
        this.id = attachmentWithId.uuid;
        switch (attachment.kind) {
            case Image:
                this.type = "image";
                break;
            case Video:
                this.type = "video";
                break;
        }
        if (ApolloApiConfig.S3_OPTIONS != null) {
            this.url = String.format("%s/%s",
                    ApolloApiConfig.S3_OPTIONS.url,
                    attachment.path
            );
        } else {
            this.url = String.format("%s/%s/%s",
                    ApolloConfig.API_URL,
                    ApolloApiConfig.STATIC_FILE_URL_PATH_NAME,
                    attachment.path
            );
        }
        this.preview_url = this.url;
        this.description = attachment.description;
    }
}