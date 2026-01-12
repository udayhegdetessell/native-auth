package com.tessell.entra.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NativeAuthResponse {

    @JsonProperty("continuation_token")
    private String continuationToken;

    @JsonProperty("challenge_type")
    private String challengeType;

    @JsonProperty("binding_method")
    private String bindingMethod;

    @JsonProperty("challenge_channel")
    private String challengeChannel;

    @JsonProperty("challenge_target_label")
    private String challengeTargetLabel;

    @JsonProperty("code_length")
    private Integer codeLength;

    @JsonProperty("interval")
    private Integer interval;

    // Error fields
    @JsonProperty("error")
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;

    @JsonProperty("error_codes")
    private int[] errorCodes;

    @JsonProperty("suberror")
    private String subError;

    // Unverified attributes (for verification_required error)
    @JsonProperty("unverified_attributes")
    private List<Map<String, String>> unverifiedAttributes;
}

