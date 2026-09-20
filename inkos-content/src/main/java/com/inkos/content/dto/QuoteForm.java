package com.inkos.content.dto;

import com.inkos.common.core.validate.ValidGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 首页语句新增 / 修改表单。 */
public record QuoteForm(
        @NotNull(message = "语句 id 不能为空", groups = ValidGroup.Update.class)
        Long id,

        @NotBlank(message = "语句内容不能为空")
        @Size(max = 180, message = "语句内容长度不能超过 180")
        String content,

        @Size(max = 80, message = "署名长度不能超过 80")
        String attribution,

        Integer sortOrder,

        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1)
        Integer status,

        @Size(max = 80, message = "首屏标题不能超过 80 字")
        String headline,

        @Size(max = 240, message = "首屏说明不能超过 240 字")
        String description
) {
}
