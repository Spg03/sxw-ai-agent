package com.sxw.sxwaiagent.treehole.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTreeholeRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 5000) String content
) {
}
