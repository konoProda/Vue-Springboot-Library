package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.LendRecord;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.service.LendRecordService;
import com.example.demo.service.OperationLogService;
import com.example.demo.utils.QueryUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/LendRecord")
public class LendRecordController {
    @Resource
    LendRecordMapper LendRecordMapper;

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

        lendRecordService.deleteById(id);
        logOperation(request, "DELETE_LEND_RECORD", String.valueOf(id), null, null);
        return Result.success();
    }

    /** 按请求体中 id 删除单条借阅记录（前端兼容路径）。 */
    @PostMapping("/deleteRecord")
    public Result<?> deleteRecord(@RequestBody LendRecord lendRecord, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        lendRecordService.deleteById(lendRecord.getId());
        logOperation(request, "DELETE_LEND_RECORD",
                lendRecord.getIsbn(), lendRecord.getBookname(), null);
        return Result.success();
    }

    /** 批量删除借阅记录。 */
    @PostMapping("/deleteRecords")
    public Result<?> deleteRecords(@RequestBody List<LendRecord> lendRecords, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        lendRecordService.deleteRecords(lendRecords);
        Map<String, Object> detail = new HashMap<>();
        detail.put("count", lendRecords.size());
        logOperation(request, "DELETE_LEND_RECORD",
                "批量" + lendRecords.size() + "条", null, detail);
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
        QueryUtils.likeIfNotBlank(wrappers, LendRecord::getReaderId, search3);
        wrappers.orderByDesc(LendRecord::getLendTime);
        Page<LendRecord> LendRecordPage =LendRecordMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(LendRecordPage);
    }

}
