package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Book;

import java.util.List;

/**
 * 图书 Service 层，封装 BookMapper 的 CRUD 操作。
 */
public interface BookService {

    /** 新增图书，返回影响行数 */
    int save(Book book);

    /** 按 ID 查询 */
    Book getById(Integer id);

    /** 按 ID 更新（参与乐观锁），返回影响行数 */
    int updateById(Book book);

    /** 按 ID 批量查询 */
    List<Book> listByIds(List<Integer> ids);

    /** 按 ID 批量删除 */
    int removeByIds(List<Integer> ids);

    /** 按 ID 删除 */
    int removeById(Long id);

    /** 分页查询 */
    Page<Book> page(Page<Book> page, LambdaQueryWrapper<Book> wrapper);
}
