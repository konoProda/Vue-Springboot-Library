package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.service.BookService;
import com.example.demo.service.OperationLogService;
import com.example.demo.utils.QueryUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/book")
public class BookController {
    @Resource
    BookService bookService;

    @Resource
    OperationLogService operationLogService;

    // ==================== 管理员专属接口 ====================

    @PostMapping
    public Result<?> save(@RequestBody Book Book, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        bookService.save(Book);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("isbn", Book.getIsbn());
        detail.put("bookName", "《" + Book.getName() + "》");
        detail.put("totalCopies", Book.getTotalCopies());
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "ADD_BOOK", detail);
        return Result.success();
    }

    @PutMapping
    public Result<?> update(@RequestBody Book Book, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            // 读者调用：借书/还书兼容路径 — no-op
            return Result.success();
        }
        Book oldBook = bookService.getById(Book.getId());
        bookService.updateById(Book);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("isbn", Book.getIsbn() != null ? Book.getIsbn() : (oldBook != null ? oldBook.getIsbn() : ""));
        detail.put("bookName", "《" + (Book.getName() != null ? Book.getName() : (oldBook != null ? oldBook.getName() : "")) + "》");
        if (oldBook != null) {
            if (Book.getTotalCopies() != null && !Book.getTotalCopies().equals(oldBook.getTotalCopies()))
                detail.put("totalCopies", oldBook.getTotalCopies() + "→" + Book.getTotalCopies());
            if (Book.getAvailableCopies() != null && !Book.getAvailableCopies().equals(oldBook.getAvailableCopies()))
                detail.put("availableCopies", oldBook.getAvailableCopies() + "→" + Book.getAvailableCopies());
        }
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "EDIT_BOOK", detail);
        return Result.success();
    }

    @PostMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestBody List<Integer> ids, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        List<Book> books = bookService.listByIds(ids);
        bookService.removeByIds(ids);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("count", ids.size());
        for (Book b : books) {
            detail.put("book", (b.getIsbn() != null ? b.getIsbn() + " " : "") + "《" + b.getName() + "》");
        }
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_BOOK", detail);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        Book book = bookService.getById(id.intValue());
        bookService.removeById(id);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        if (book != null) {
            detail.put("isbn", book.getIsbn());
            detail.put("bookName", "《" + book.getName() + "》");
        } else {
            detail.put("bookId", id);
        }
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_BOOK", detail);
        return Result.success();
    }

    // ==================== 公开接口 ====================

    @GetMapping
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search1,
                              @RequestParam(defaultValue = "") String search2,
                              @RequestParam(defaultValue = "") String search3){
        LambdaQueryWrapper<Book> wrappers = Wrappers.<Book>lambdaQuery();
        QueryUtils.likeIfNotBlank(wrappers, Book::getIsbn, search1);
        QueryUtils.likeIfNotBlank(wrappers, Book::getName, search2);
        QueryUtils.likeIfNotBlank(wrappers, Book::getAuthor, search3);
        wrappers.orderByDesc(Book::getId);
        Page<Book> BookPage = bookService.page(new Page<>(pageNum, pageSize), wrappers);
        return Result.success(BookPage);
    }
}
