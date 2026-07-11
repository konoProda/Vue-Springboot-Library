package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.mapper.BookMapper;
import com.example.demo.service.BorrowService;
import com.example.demo.service.OperationLogService;
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
    BookMapper BookMapper;

    @Resource
    BorrowService borrowService;

    @Resource
    OperationLogService operationLogService;

    // ==================== 管理员专属接口 ====================

    @PostMapping
    public Result<?> save(@RequestBody Book Book, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        BookMapper.insert(Book);
        // 操作日志
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

    /**
     * 更新图书信息。
     * - 管理员 (role=1): 执行常规编辑（如修改书名、作者、副本数等）
     * - 读者 (role≠1): 前端借书/还书流程中仍会调用此接口（携带已移除的 status 字段），
     *   此处返回空成功，实际的借/还逻辑由 BorrowService 统一处理
     *   （通过 BookWithUserController / LendRecordController1 触发）。
     */
    @PutMapping
    public  Result<?> update(@RequestBody Book Book, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            // 读者调用：借书/还书兼容路径 — no-op
            // 真实业务由 BorrowService（经 BookWithUserController / LendRecordController1）处理
            return Result.success();
        }
        // 管理员编辑图书信息
        Book oldBook = BookMapper.selectById(Book.getId());
        BookMapper.updateById(Book);
        // 操作日志 — 含前后对比
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
    public  Result<?> deleteBatch(@RequestBody List<Integer> ids, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        // 删前查询，用于日志
        List<Book> books = BookMapper.selectBatchIds(ids);
        BookMapper.deleteBatchIds(ids);
        // 记录操作日志
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
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        Book book = BookMapper.selectById(id.intValue());
        BookMapper.deleteById(id);
        // 记录操作日志
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
        if(StringUtils.isNotBlank(search1)){
            wrappers.like(Book::getIsbn,search1);
        }
        if(StringUtils.isNotBlank(search2)){
            wrappers.like(Book::getName,search2);
        }
        if(StringUtils.isNotBlank(search3)){
            wrappers.like(Book::getAuthor,search3);
        }
        wrappers.orderByDesc(Book::getId);
        Page<Book> BookPage =BookMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(BookPage);
    }
}
