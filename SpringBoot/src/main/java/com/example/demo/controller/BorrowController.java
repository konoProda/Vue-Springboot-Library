package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.mapper.BookMapper;
import com.example.demo.service.BorrowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 借书 / 还书 / 续借 统一业务接口。
 * <p>
 * 每个操作只接收 isbn，userId 从 JWT 中提取。
 * 后端统一完成校验、日期计算、库存更新、借阅记录更新。
 *
 * <pre>
 * POST /api/borrow   {"isbn":"..."}
 * POST /api/return   {"isbn":"..."}
 * POST /api/renew    {"isbn":"..."}
 * </pre>
 */
@RestController
public class BorrowController {

    @Resource
    private BookMapper bookMapper;

    @Resource
    private BorrowService borrowService;

    /** 根据 isbn 查找图书 ID */
    private Long getBookIdByIsbn(String isbn) {
        LambdaQueryWrapper<Book> qw = new LambdaQueryWrapper<>();
        qw.eq(Book::getIsbn, isbn);
        Book book = bookMapper.selectOne(qw);
        if (book == null) {
            throw new RuntimeException("图书不存在");
        }
        return (long) book.getId();
    }

    /** 从 JWT 拦截器设置的 request 属性中提取 userId */
    private Long getUserId(HttpServletRequest request) {
        Integer uid = (Integer) request.getAttribute(JwtInterceptor.REQUEST_ATTR_USER_ID);
        if (uid == null) {
            throw new RuntimeException("未登录");
        }
        return (long) uid;
    }

    // ==================== 借书 ====================

    @PostMapping("/borrow")
    public Result<?> borrow(@RequestBody Map<String, String> body,
                            HttpServletRequest request) {
        String isbn = body.get("isbn");
        if (isbn == null || isbn.isEmpty()) {
            return Result.error("1", "缺少 isbn 参数");
        }
        try {
            Long userId = getUserId(request);
            Long bookId = getBookIdByIsbn(isbn);
            borrowService.borrowBook(userId, bookId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }
    }

    // ==================== 还书 ====================

    @PostMapping("/return")
    public Result<?> returnBook(@RequestBody Map<String, String> body,
                                HttpServletRequest request) {
        String isbn = body.get("isbn");
        if (isbn == null || isbn.isEmpty()) {
            return Result.error("1", "缺少 isbn 参数");
        }
        try {
            Long userId = getUserId(request);
            Long bookId = getBookIdByIsbn(isbn);
            borrowService.returnBook(userId, bookId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }
    }

    // ==================== 续借 ====================

    @PostMapping("/renew")
    public Result<?> renew(@RequestBody Map<String, String> body,
                           HttpServletRequest request) {
        String isbn = body.get("isbn");
        if (isbn == null || isbn.isEmpty()) {
            return Result.error("1", "缺少 isbn 参数");
        }
        try {
            Long userId = getUserId(request);
            Long bookId = getBookIdByIsbn(isbn);
            borrowService.renewBook(userId, bookId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }
    }
}
