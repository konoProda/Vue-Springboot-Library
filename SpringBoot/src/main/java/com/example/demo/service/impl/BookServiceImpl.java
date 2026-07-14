package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.entity.Book;
import com.example.demo.mapper.BookMapper;
import com.example.demo.service.BookService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BookServiceImpl implements BookService {

    @Resource
    private BookMapper bookMapper;

    @Override
    public int save(Book book) {
        return bookMapper.insert(book);
    }

    @Override
    public Book getById(Integer id) {
        return bookMapper.selectById(id);
    }

    @Override
    public int updateById(Book book) {
        return bookMapper.updateById(book);
    }

    @Override
    public List<Book> listByIds(List<Integer> ids) {
        return bookMapper.selectBatchIds(ids);
    }

    @Override
    public int removeByIds(List<Integer> ids) {
        return bookMapper.deleteBatchIds(ids);
    }

    @Override
    public int removeById(Long id) {
        return bookMapper.deleteById(id);
    }

    @Override
    public Page<Book> page(Page<Book> page, LambdaQueryWrapper<Book> wrapper) {
        return bookMapper.selectPage(page, wrapper);
    }
}
