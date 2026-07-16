package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.commom.Result;
import com.example.demo.entity.BookWithUser;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.mapper.BookWithUserMapper;
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
    private OperationLogService operationLogService;

    @PostMapping
    public Result<?> update(@RequestBody BookWithUser bookWithUser, HttpServletRequest request){
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

    @PostMapping("/deleteRecords")
    public Result<?> deleteRecords(@RequestBody List<BookWithUser> bookWithUsers, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        // 活跃借阅不能直接删除，需通过还书流程处理
        return Result.error("1", "活跃借阅记录不能直接删除。请通过还书流程（借阅状态页点击'还书'按钮）或借阅管理页编辑归还状态来处理。");
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
        // 借阅状态筛选
        if ("1".equals(overdueFilter)) {
            wrappers.lt(BookWithUser::getDeadtime, new Date());       // 逾期未还
        } else if ("2".equals(overdueFilter)) {
            wrappers.ge(BookWithUser::getDeadtime, new Date());       // 未逾期
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
