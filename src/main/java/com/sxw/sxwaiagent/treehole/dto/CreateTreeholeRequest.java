package com.sxw.sxwaiagent.treehole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTreeholeRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotBlank @Pattern(regexp = "开心|平静|疲惫|焦虑|难过") String mood
) {
}
