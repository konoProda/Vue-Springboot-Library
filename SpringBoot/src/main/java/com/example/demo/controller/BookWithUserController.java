package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.entity.BookWithUser;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.service.BorrowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bookwithuser")
public class BookWithUserController {

    @Resource
    BookWithUserMapper BookWithUserMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private BorrowService borrowService;

    /**
     * 借书操作：委托 BorrowService.borrowBook() 统一处理。
     * BorrowService 在同一事务中完成:
     *   1. 校验图书可借 + 用户借阅数 < maxBorrowCount
     *   2. 校验不重复借阅
     *   3. 更新 book.status / borrownum
     *   4. 插入 lend_record
     *   5. 插入 bookwithuser
     */
    @PostMapping("/insertNew")
    public Result<?> insertNew(@RequestBody BookWithUser bookWithUser){
        // 通过 isbn 查找图书获取 bookId
        LambdaQueryWrapper<Book> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Book::getIsbn, bookWithUser.getIsbn());
        Book book = bookMapper.selectOne(queryWrapper);
        if (book == null) {
            return Result.error("1", "图书不存在");
        }

        borrowService.borrowBook(
                (long) bookWithUser.getUserId(),   // userId (原 BookWithUser.id 存储的是用户ID)
                (long) book.getId()                 // bookId
        );
        return Result.success();
    }

    /**
     * 更新 bookwithuser 记录。
     * - 续借场景（prolong 减少）: 委托 BorrowService.renewBook() 处理
     * - 管理员编辑场景: 保持原有 UpdateWrapper 逻辑
     */
    @PostMapping
    public Result<?> update(@RequestBody BookWithUser bookWithUser){
        // 判断是否为续借操作：查出现有记录，若 prolong 在减少则为续借
        LambdaQueryWrapper<BookWithUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BookWithUser::getIsbn, bookWithUser.getIsbn())
                    .eq(BookWithUser::getUserId, bookWithUser.getUserId());
        BookWithUser existing = BookWithUserMapper.selectOne(queryWrapper);

        if (existing != null
                && bookWithUser.getProlong() != null
                && existing.getProlong() != null
                && bookWithUser.getProlong() < existing.getProlong()) {
            // 续借操作：prolong 值减少 → 委托 BorrowService
            LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
            bookQuery.eq(Book::getIsbn, bookWithUser.getIsbn());
            Book book = bookMapper.selectOne(bookQuery);
            if (book == null) {
                return Result.error("1", "图书不存在");
            }

            borrowService.renewBook(
                    (long) bookWithUser.getUserId(),
                    (long) book.getId()
            );
            return Result.success();
        }

        // 管理员编辑：保持原有更新逻辑
        UpdateWrapper<BookWithUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", bookWithUser.getIsbn())
                     .eq("user_id", bookWithUser.getUserId());
        BookWithUserMapper.update(bookWithUser, updateWrapper);
        return Result.success();
    }

    /**
     * 还书时的 bookwithuser 删除已由 BorrowService.returnBook() 统一处理，
     * 此处保留端点以维持前端兼容性（直接返回成功）。
     */
    @PostMapping("/deleteRecord")
    public Result<?> deleteRecord(@RequestBody BookWithUser bookWithUser){
        // bookwithuser 删除已迁移至 BorrowService.returnBook()
        return Result.success();
    }

    @PostMapping("/deleteRecords")
    public Result<?> deleteRecords(@RequestBody List<BookWithUser> bookWithUsers){
        for (BookWithUser curRecord : bookWithUsers) {
            Map<String, Object> map = new HashMap<>();
            map.put("isbn", curRecord.getIsbn());
            map.put("user_id", curRecord.getUserId());
            BookWithUserMapper.deleteByMap(map);
        }
        return Result.success();
    }

    @GetMapping
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search1,
                              @RequestParam(defaultValue = "") String search2,
                              @RequestParam(defaultValue = "") String search3){
        LambdaQueryWrapper<BookWithUser> wrappers = Wrappers.<BookWithUser>lambdaQuery();
        if(StringUtils.isNotBlank(search1)){
            wrappers.like(BookWithUser::getIsbn, search1);
        }
        if(StringUtils.isNotBlank(search2)){
            wrappers.like(BookWithUser::getBookName, search2);
        }
        if(StringUtils.isNotBlank(search3)){
            wrappers.like(BookWithUser::getUserId, search3);
        }
        Page<BookWithUser> BookPage = BookWithUserMapper.selectPage(new Page<>(pageNum, pageSize), wrappers);
        return Result.success(BookPage);
    }
}
