package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.Book;
import com.example.demo.entity.BookWithUser;
import com.example.demo.entity.LendRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.OperationLogService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/LendRecord")
public class LendRecordController {
    @Resource
    LendRecordMapper LendRecordMapper;

    @Resource
    private BookWithUserMapper bookWithUserMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    OperationLogService operationLogService;

    // ==================== 管理员专属接口 ====================

    /**
     * 按 ID 删除单条借阅记录。
     * 若记录未归还(status="0")，同步删除 bookwithuser 并恢复库存。
     */
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        syncDeleteById(id);
        logOperation(request, "DELETE_LEND_RECORD", String.valueOf(id), null, null);
        return Result.success();
    }

    /**
     * 按请求体中 id 删除单条借阅记录（前端兼容路径）。
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/deleteRecord")
    public Result<?> deleteRecord(@RequestBody LendRecord lendRecord, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        syncDeleteById(lendRecord.getId());
        logOperation(request, "DELETE_LEND_RECORD",
                lendRecord.getIsbn(), lendRecord.getBookname(), null);
        return Result.success();
    }

    /**
     * 批量删除借阅记录。
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/deleteRecords")
    public Result<?> deleteRecords(@RequestBody List<LendRecord> lendRecords, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        for (LendRecord rec : lendRecords) {
            syncDeleteById(rec.getId());
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("count", lendRecords.size());
        logOperation(request, "DELETE_LEND_RECORD",
                "批量" + lendRecords.size() + "条", null, detail);
        return Result.success();
    }

    /**
     * 按 ID 删除 lend_record，若未归还则同步清理 bookwithuser 和 book。
     */
    private void syncDeleteById(Long id) {
        LendRecord record = LendRecordMapper.selectById(id);
        if (record == null) return;

        // 若未归还 → 删除活跃借阅 + 恢复库存
        if ("0".equals(record.getStatus())) {
            Map<String, Object> deleteMap = new HashMap<>();
            deleteMap.put("isbn", record.getIsbn());
            deleteMap.put("user_id", record.getReaderId());
            bookWithUserMapper.deleteByMap(deleteMap);

            LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
            bookQuery.eq(Book::getIsbn, record.getIsbn());
            Book book = bookMapper.selectOne(bookQuery);
            if (book != null) {
                book.setAvailableCopies(book.getAvailableCopies() != null
                        ? book.getAvailableCopies() + 1 : 1);
                bookMapper.updateById(book);
            }
        }

        LendRecordMapper.deleteById(id);
    }

    /**
     * 编辑借阅记录 — 使用记录 ID 精确定位，支持跨表同步。
     * 当 status 从 "0" 改为 "1" 时，同步删除 bookwithuser 并恢复 book.availableCopies。
     */
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody LendRecord lendRecord, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }

        // ========== 1. 查旧记录 ==========
        LendRecord oldRecord = LendRecordMapper.selectById(id);
        if (oldRecord == null) {
            return Result.error("1", "借阅记录不存在");
        }
        boolean wasReturned = "1".equals(oldRecord.getStatus());
        boolean nowReturned = "1".equals(lendRecord.getStatus());

        // ========== 2. 处理状态变更 ==========
        String isbn = oldRecord.getIsbn();
        Integer readerId = oldRecord.getReaderId();

        // ---- 正向：未归还 → 已归还 ----
        if (!wasReturned && nowReturned) {
            // 设置归还时间（如果前端未传）
            if (lendRecord.getReturnTime() == null) {
                lendRecord.setReturnTime(new Date());
            }
            LendRecordMapper.updateById(lendRecord);

            // 删除 bookwithuser 活跃记录
            Map<String, Object> deleteMap = new HashMap<>();
            deleteMap.put("isbn", isbn);
            deleteMap.put("user_id", readerId);
            bookWithUserMapper.deleteByMap(deleteMap);

            // 恢复 book.availableCopies
            LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
            bookQuery.eq(Book::getIsbn, isbn);
            Book book = bookMapper.selectOne(bookQuery);
            if (book != null) {
                book.setAvailableCopies(book.getAvailableCopies() != null
                        ? book.getAvailableCopies() + 1 : 1);
                bookMapper.updateById(book);
            }
        }
        // ---- 逆向：已归还 → 未归还 ----
        else if (wasReturned && !nowReturned) {
            // 清空归还时间
            lendRecord.setReturnTime(null);
            lendRecord.setStatus("0");
            LendRecordMapper.updateById(lendRecord);

            // 检查库存
            LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
            bookQuery.eq(Book::getIsbn, isbn);
            Book book = bookMapper.selectOne(bookQuery);
            if (book == null || book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
                throw new RuntimeException("库存不足，无法恢复为未归还状态");
            }
            book.setAvailableCopies(book.getAvailableCopies() - 1);
            bookMapper.updateById(book);

            // 创建 bookwithuser 活跃记录（避免重复）
            LambdaQueryWrapper<BookWithUser> existCheck = new LambdaQueryWrapper<>();
            existCheck.eq(BookWithUser::getUserId, readerId)
                      .eq(BookWithUser::getIsbn, isbn);
            if (bookWithUserMapper.selectCount(existCheck) == 0) {
                User user = userMapper.selectById(readerId);
                Calendar cal = Calendar.getInstance();
                cal.setTime(oldRecord.getLendTime() != null ? oldRecord.getLendTime() : new Date());
                cal.add(Calendar.DAY_OF_MONTH, 30);

                BookWithUser bw = new BookWithUser();
                bw.setUserId(readerId);
                bw.setIsbn(isbn);
                bw.setBookName(oldRecord.getBookname());
                bw.setNickName(user != null ? user.getNickName() : "");
                bw.setLendtime(oldRecord.getLendTime());
                bw.setDeadtime(cal.getTime());
                bw.setProlong(1);
                bookWithUserMapper.insert(bw);
            }
        }
        // ---- 状态未变：仅更新字段 ----
        else {
            LendRecordMapper.updateById(lendRecord);
        }

        // ========== 3. 操作日志 ==========
        logEditLendRecord(request, oldRecord, lendRecord);
        return Result.success();
    }

    @PutMapping("/byTime/{lendTime}")
    public  Result<?> update2(@PathVariable Date lendTime, @RequestBody LendRecord lendRecord, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        UpdateWrapper<LendRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("lendTime",lendTime);
        LendRecord lendrecord = new LendRecord();
        lendrecord.setReturnTime(lendRecord.getReturnTime());
        lendrecord.setStatus(lendRecord.getStatus());
        LendRecordMapper.update(lendrecord, updateWrapper);
        logOperation(request, "EDIT_LEND_RECORD", lendRecord.getIsbn(), lendRecord.getBookname(), null);
        return Result.success();
    }

    // ==================== 辅助方法 ====================

    private void logEditLendRecord(HttpServletRequest request, LendRecord before, LendRecord after) {
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("isbn", before.getIsbn());
        detail.put("recordId", before.getId());
        if (before.getBookname() != null) detail.put("bookName", "《" + before.getBookname() + "》");
        detail.put("readerId", before.getReaderId());
        if (!"0".equals(before.getStatus()) || !"0".equals(after.getStatus())) {
            detail.put("beforeStatus", "0".equals(before.getStatus()) ? "未归还" : "已归还");
        }
        detail.put("afterStatus", "0".equals(after.getStatus()) ? "未归还" : "已归还");
        if (after.getReturnTime() != null) {
            detail.put("afterReturnTime", after.getReturnTime());
        }
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "EDIT_LEND_RECORD", detail);
    }

    private void logOperation(HttpServletRequest request, String type, String isbn, String bookname, Map<String, Object> extra) {
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("isbn", isbn);
        if (bookname != null) detail.put("bookName", "《" + bookname + "》");
        if (extra != null) detail.putAll(extra);
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, type, detail);
    }

    // ==================== 通用接口（所有已登录用户） ====================
    /**
     * 借书时的 lend_record 插入已由 BorrowService.borrowBook() 统一处理，
     * 此处保留端点以维持前端兼容性（直接返回成功）。
     */
    @PostMapping
    public Result<?> save(@RequestBody LendRecord LendRecord){
        // lend_record 插入已迁移至 BorrowService.borrowBook()
        return Result.success();
    }
    @GetMapping
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search1,
                              @RequestParam(defaultValue = "") String search2,
                              @RequestParam(defaultValue = "") String search3){
        LambdaQueryWrapper<LendRecord> wrappers = Wrappers.<LendRecord>lambdaQuery();
        if(StringUtils.isNotBlank(search1)){
            wrappers.like(LendRecord::getIsbn,search1);
        }
        if(StringUtils.isNotBlank(search2)){
            wrappers.like(LendRecord::getBookname,search2);
        }
        if(StringUtils.isNotBlank(search3)){
            wrappers.like(LendRecord::getReaderId,search3);
        }
        wrappers.orderByDesc(LendRecord::getLendTime);
        Page<LendRecord> LendRecordPage =LendRecordMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(LendRecordPage);
    }

}
