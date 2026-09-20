package com.inkos.content.dto;
import com.inkos.common.core.validate.ValidGroup;
import jakarta.validation.constraints.*;
public record TagForm(
    @NotNull(groups = ValidGroup.Update.class) Long id,
    @NotBlank @Size(max = 64) String name,
    @Size(max = 64) String slug
) {}
