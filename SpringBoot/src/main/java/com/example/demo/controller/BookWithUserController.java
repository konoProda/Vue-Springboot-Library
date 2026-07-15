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
import java.util.Date;
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
                              @RequestParam(defaultValue = "") String search3,
                              @RequestParam(defaultValue = "") String overdueFilter){
        LambdaQueryWrapper<BookWithUser> wrappers = new LambdaQueryWrapper<>();
        QueryUtils.likeIfNotBlank(wrappers, BookWithUser::getIsbn, search1);
        QueryUtils.likeIfNotBlank(wrappers, BookWithUser::getBookName, search2);
        QueryUtils.likeIfNotBlank(wrappers, BookWithUser::getUserId, search3);
        // 逾期筛选：仅显示已逾期且未归还
        if ("1".equals(overdueFilter)) {
            wrappers.lt(BookWithUser::getDeadtime, new Date());
        }
        wrappers.orderByDesc(BookWithUser::getLendtime);
        Page<BookWithUser> BookPage = BookWithUserMapper.selectPage(new Page<>(pageNum, pageSize), wrappers);

        // 计算借阅状态和逾期天数
        Date now = new Date();
        for (BookWithUser bw : BookPage.getRecords()) {
            computeStatus(bw, now);
        }

        return Result.success(BookPage);
    }

    /** 根据当前时间计算借阅状态和逾期天数 */
    private void computeStatus(BookWithUser bw, Date now) {
        if (bw.getDeadtime() == null) {
            bw.setStatus("正常");
            bw.setOverdueDays(0);
            return;
        }
        long diffMs = now.getTime() - bw.getDeadtime().getTime();
        long diffDays = diffMs / (1000 * 60 * 60 * 24);
        if (diffDays > 0) {
            bw.setStatus("已逾期");
            bw.setOverdueDays((int) diffDays);
        } else if (diffDays >= -3) {
            bw.setStatus("即将到期");
            bw.setOverdueDays(0);
        } else {
            bw.setStatus("正常");
            bw.setOverdueDays(0);
        }
    }
}
