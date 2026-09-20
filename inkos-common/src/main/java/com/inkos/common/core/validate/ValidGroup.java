package com.inkos.common.core.validate;

/**
 * 校验分组，用于区分新增与修改场景的约束差异。
 *
 * <pre>
 *   {@code @Null(groups = ValidGroup.Create.class)}
 *   {@code @NotNull(groups = ValidGroup.Update.class)}
 * </pre>
 */
public interface ValidGroup {

    /** 新增场景 */
    interface Create extends jakarta.validation.groups.Default {
    }

    /** 修改场景 */
    interface Update extends jakarta.validation.groups.Default {
    }

    /** 查询场景 */
    interface Query {
    }
}
