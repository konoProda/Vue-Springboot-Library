package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.entity.User;
import com.example.demo.mapper.BookMapper;
import com.example.demo.service.BorrowService;
import com.example.demo.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/book")
public class BookController {
    @Resource
    BookMapper BookMapper;

    @Resource
    BorrowService borrowService;

    // ==================== 管理员专属接口 ====================

    @PostMapping
    public Result<?> save(@RequestBody Book Book, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        BookMapper.insert(Book);
        return Result.success();
    }

    /**
     * 更新图书信息。
     * - 当 status 变更时（借书/还书），通过 BorrowService 统一处理事务（读者可操作）
     * - 常规编辑（不改 status）为管理员专属操作
     */
    @PutMapping
    public  Result<?> update(@RequestBody Book Book, HttpServletRequest request){
        // 借书(status="0") / 还书(status="1") → 委托 BorrowService
        if ("0".equals(Book.getStatus()) || "1".equals(Book.getStatus())) {
            User currentUser = TokenUtils.getUser();
            if (currentUser != null && Book.getId() != null) {
                if ("0".equals(Book.getStatus())) {
                    borrowService.borrowBook((long) currentUser.getId(), (long) Book.getId());
                } else {
                    borrowService.returnBook((long) currentUser.getId(), (long) Book.getId());
                }
                return Result.success();
            }
            // 无 token 时不做本地 updateById，避免和 BorrowService 重复写入
            return Result.success();
        }
        // 常规编辑 → 管理员专属
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        BookMapper.updateById(Book);
        return Result.success();
    }

    @PostMapping("/deleteBatch")
    public  Result<?> deleteBatch(@RequestBody List<Integer> ids, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        BookMapper.deleteBatchIds(ids);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        BookMapper.deleteById(id);
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
        Page<Book> BookPage =BookMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(BookPage);
    }
}
