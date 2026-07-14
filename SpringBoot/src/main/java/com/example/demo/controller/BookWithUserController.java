package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.entity.BookWithUser;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.service.BorrowService;
import com.example.demo.service.OperationLogService;
import com.example.demo.utils.QueryUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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

    @Resource
    private OperationLogService operationLogService;

    @PostMapping("/insertNew")
    public Result<?> insertNew(@RequestBody BookWithUser bookWithUser){
        LambdaQueryWrapper<Book> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Book::getIsbn, bookWithUser.getIsbn());
        Book book = bookMapper.selectOne(queryWrapper);
        if (book == null) {
            return Result.error("1", "图书不存在");
        }

        try {
            borrowService.borrowBook(
                    (long) bookWithUser.getUserId(),
                    (long) book.getId()
            );
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }
        return Result.success();
    }

    @PostMapping
    public Result<?> update(@RequestBody BookWithUser bookWithUser, HttpServletRequest request){
        LambdaQueryWrapper<BookWithUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BookWithUser::getIsbn, bookWithUser.getIsbn())
                    .eq(BookWithUser::getUserId, bookWithUser.getUserId());
        BookWithUser existing = BookWithUserMapper.selectOne(queryWrapper);

        if (existing != null
                && bookWithUser.getProlong() != null
                && existing.getProlong() != null
                && bookWithUser.getProlong() < existing.getProlong()) {
            LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
            bookQuery.eq(Book::getIsbn, bookWithUser.getIsbn());
            Book book = bookMapper.selectOne(bookQuery);
            if (book == null) {
                return Result.error("1", "图书不存在");
            }

            try {
                borrowService.renewBook(
                        (long) bookWithUser.getUserId(),
                        (long) book.getId()
                );
            } catch (RuntimeException e) {
                return Result.error("1", e.getMessage());
            }
            return Result.success();
        }

        // 管理员编辑
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        UpdateWrapper<BookWithUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", bookWithUser.getIsbn())
                     .eq("user_id", bookWithUser.getUserId());
        BookWithUserMapper.update(bookWithUser, updateWrapper);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("isbn", bookWithUser.getIsbn());
        detail.put("bookName", bookWithUser.getBookName());
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "EDIT_BOOKWITHUSER", detail);
        return Result.success();
    }

    @PostMapping("/deleteRecord")
    public Result<?> deleteRecord(@RequestBody BookWithUser bookWithUser){
        return Result.success();
    }

    @PostMapping("/deleteRecords")
    public Result<?> deleteRecords(@RequestBody List<BookWithUser> bookWithUsers, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

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
        LambdaQueryWrapper<BookWithUser> wrappers = new LambdaQueryWrapper<>();
        QueryUtils.likeIfNotBlank(wrappers, BookWithUser::getIsbn, search1);
        QueryUtils.likeIfNotBlank(wrappers, BookWithUser::getBookName, search2);
        QueryUtils.likeIfNotBlank(wrappers, BookWithUser::getUserId, search3);
        wrappers.orderByDesc(BookWithUser::getLendtime);
        Page<BookWithUser> BookPage = BookWithUserMapper.selectPage(new Page<>(pageNum, pageSize), wrappers);
        return Result.success(BookPage);
    }
}
