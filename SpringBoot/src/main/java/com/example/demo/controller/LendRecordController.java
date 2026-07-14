package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.LendRecord;
import com.example.demo.entity.User;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.LendRecordService;
import com.example.demo.service.OperationLogService;
import com.example.demo.utils.QueryUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/LendRecord")
public class LendRecordController {
    @Resource
    LendRecordMapper LendRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private LendRecordService lendRecordService;

    @Resource
    OperationLogService operationLogService;

    // ==================== 管理员专属接口 ====================

    /** 按 ID 删除单条借阅记录。 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        // 删除前查询记录以获取借阅者信息，用于操作日志
        LendRecord record = LendRecordMapper.selectById(id);

        try {
            lendRecordService.deleteById(id);
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }

        logDeleteOperation(request, record);
        return Result.success();
    }

    /** 按请求体中 id 删除单条借阅记录（前端兼容路径）。 */
    @PostMapping("/deleteRecord")
    public Result<?> deleteRecord(@RequestBody LendRecord lendRecord, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        // 删除前先查询完整记录（请求体可能只有 id，不包含 isbn/bookname/readerId）
        LendRecord fullRecord = LendRecordMapper.selectById(lendRecord.getId());

        try {
            lendRecordService.deleteById(lendRecord.getId());
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }

        logDeleteOperation(request, fullRecord != null ? fullRecord : lendRecord);
        return Result.success();
    }

    /** 批量删除借阅记录。 */
    @PostMapping("/deleteRecords")
    public Result<?> deleteRecords(@RequestBody List<LendRecord> lendRecords, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        // 删除前批量查询以获取完整记录信息
        List<Long> ids = lendRecords.stream().map(LendRecord::getId).collect(Collectors.toList());
        List<LendRecord> fullRecords = LendRecordMapper.selectBatchIds(ids);

        try {
            lendRecordService.deleteRecords(lendRecords);
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }

        logBatchDeleteOperation(request, fullRecords);
        return Result.success();
    }

    /** 编辑借阅记录 — 委托 LendRecordService 处理多表同步。 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody LendRecord lendRecord, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        LendRecord oldRecord = LendRecordMapper.selectById(id);
        if (oldRecord == null) {
            return Result.error("1", "借阅记录不存在");
        }

        try {
            lendRecordService.updateLendRecord(id, lendRecord);
        } catch (RuntimeException e) {
            return Result.error("1", e.getMessage());
        }

        logEditLendRecord(request, oldRecord, lendRecord);
        return Result.success();
    }

    // ==================== 日志辅助方法 ====================

    /** 构建借阅者标签：昵称（ID） */
    private String buildReaderLabel(Integer readerId) {
        if (readerId == null) return "未知读者";
        User reader = userMapper.selectById(readerId);
        if (reader != null) {
            String name = reader.getNickName() != null ? reader.getNickName() : reader.getUsername();
            return name + "（ID:" + readerId + "）";
        }
        return "读者ID:" + readerId;
    }

    /** 单条删除操作日志 */
    private void logDeleteOperation(HttpServletRequest request, LendRecord record) {
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        if (record != null) {
            detail.put("isbn", record.getIsbn());
            if (record.getBookname() != null) detail.put("bookName", "《" + record.getBookname() + "》");
            detail.put("readerId", record.getReaderId());
            detail.put("reader", buildReaderLabel(record.getReaderId()));
        }
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_LEND_RECORD", detail);
    }

    /** 批量删除操作日志 */
    private void logBatchDeleteOperation(HttpServletRequest request, List<LendRecord> records) {
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("count", records.size());
        List<String> items = new ArrayList<>();
        for (LendRecord rec : records) {
            items.add(buildReaderLabel(rec.getReaderId()) + " — 《" + rec.getBookname() + "》");
        }
        detail.put("items", items);
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_LEND_RECORD", detail);
    }

    /** 编辑操作日志 */
    private void logEditLendRecord(HttpServletRequest request, LendRecord before, LendRecord after) {
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("recordId", before.getId());
        detail.put("reader", buildReaderLabel(before.getReaderId()));
        detail.put("readerId", before.getReaderId());
        detail.put("isbn", before.getIsbn());
        if (before.getBookname() != null) detail.put("bookName", "《" + before.getBookname() + "》");
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

    // ==================== 通用接口（所有已登录用户） ====================

    @PostMapping
    public Result<?> save(@RequestBody LendRecord LendRecord){
        return Result.success();
    }

    @GetMapping
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search1,
                              @RequestParam(defaultValue = "") String search2,
                              @RequestParam(defaultValue = "") String search3){
        LambdaQueryWrapper<LendRecord> wrappers = Wrappers.<LendRecord>lambdaQuery();
        QueryUtils.likeIfNotBlank(wrappers, LendRecord::getIsbn, search1);
        QueryUtils.likeIfNotBlank(wrappers, LendRecord::getBookname, search2);
        QueryUtils.eqIfNotBlank(wrappers, LendRecord::getReaderId, search3);
        wrappers.orderByDesc(LendRecord::getLendTime);
        Page<LendRecord> LendRecordPage =LendRecordMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(LendRecordPage);
    }

}
