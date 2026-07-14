package com.example.demo.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * QueryWrapper 构建工具方法。
 * 提供通用的模糊查询辅助函数，减少各 Controller findPage 中的重复代码。
 */
public class QueryUtils {

    /**
     * 如果值非空，向 wrapper 添加 LIKE 条件。
     *
     * @param wrapper 查询构造器
     * @param column  目标列（方法引用，如 {@code Book::getIsbn}）
     * @param value   搜索值，为 {@code null} 或空字符串时跳过
     * @param <T>     实体类型
     */
    public static <T> void likeIfNotBlank(LambdaQueryWrapper<T> wrapper,
                                          SFunction<T, ?> column, String value) {
        if (value != null && !value.isEmpty()) {
            wrapper.like(column, value);
        }
    }

    /**
     * 如果值非空，向 wrapper 添加 EQ 条件。
     *
     * @param wrapper 查询构造器
     * @param column  目标列（方法引用）
     * @param value   搜索值
     * @param <T>     实体类型
     */
    public static <T> void eqIfNotBlank(LambdaQueryWrapper<T> wrapper,
                                        SFunction<T, ?> column, String value) {
        if (value != null && !value.isEmpty()) {
            wrapper.eq(column, value);
        }
    }
}
