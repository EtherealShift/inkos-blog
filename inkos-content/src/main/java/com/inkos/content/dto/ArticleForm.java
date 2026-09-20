package com.inkos.content.dto;

import com.inkos.common.core.validate.ValidGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 文章新增 / 修改表单。
 *
 * <p>校验分组：新增时 id 必须为空，修改时 id 必填，避免误把新增当修改。
 *
 * @param id         文章 id，新增留空
 * @param categoryId 分类 id，可为空
 * @param title      标题
 * @param slug       URL 标识，留空则由标题生成
 * @param summary    摘要，留空则由正文生成
 * @param coverUrl   封面图地址
 * @param contentMd  Markdown 正文
 * @param tagIds     标签 id 列表
 * @param visibility 可见性：0 公开，1 私密
 */
public record ArticleForm(

        @NotNull(message = "文章 id 不能为空", groups = ValidGroup.Update.class)
        @jakarta.validation.constraints.Null(groups = ValidGroup.Create.class)
        Long id,

        Long categoryId,

        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题长度不能超过 200")
        String title,

        @Size(max = 220, message = "slug 长度不能超过 220")
        String slug,

        @Size(max = 500, message = "摘要长度不能超过 500")
        String summary,

        @Size(max = 512, message = "封面地址长度不能超过 512")
        String coverUrl,

        @NotBlank(message = "正文不能为空")
        String contentMd,

        List<Long> tagIds,

        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1)
        Integer visibility
) {
}
