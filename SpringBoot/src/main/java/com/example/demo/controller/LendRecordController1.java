package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.entity.LendRecord;
import com.example.demo.mapper.BookMapper;
import com.example.demo.service.BorrowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 还书控制器。
 * 原先直接操作 lend_record 表，现已将还书逻辑迁移至 BorrowService.returnBook()。
 * 前端调用路径和参数保持不变。
 */
@RestController
@RequestMapping("/LendRecord1")
public class LendRecordController1 {

    @Resource
    private BookMapper bookMapper;

    @Resource
    private BorrowService borrowService;

    /**
     * 还书操作：通过 isbn 查找图书，委托 BorrowService 统一处理。
     * BorrowService 在同一事务中完成:
     *   1. 更新 book.status = "1"
     *   2. 更新 lend_record.returnTime + status
     *   3. 删除 bookwithuser 记录
     */
    @PutMapping
    public Result<?> update2(@RequestBody LendRecord lendRecord){
        // 通过 isbn 查找图书获取 bookId
        LambdaQueryWrapper<Book> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Book::getIsbn, lendRecord.getIsbn());
        Book book = bookMapper.selectOne(queryWrapper);
        if (book == null) {
            return Result.error("1", "图书不存在");
        }

        borrowService.returnBook(
                (long) lendRecord.getReaderId(),
                (long) book.getId()
        );
        return Result.success();
    }

}
