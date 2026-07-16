package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.LoginUser;
import com.example.demo.commom.Result;
import com.example.demo.entity.BookWithUser;
import com.example.demo.entity.User;
import com.example.demo.interceptor.JwtInterceptor;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.OperationLogService;
import com.example.demo.utils.QueryUtils;
import com.example.demo.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    UserMapper userMapper;

    @Resource
    private BookWithUserMapper bookWithUserMapper;

    @Resource
    OperationLogService operationLogService;

    // ==================== 公开接口（无需管理员权限） ====================

    @PostMapping("/register")
    public Result<?> register(@RequestBody User user){
        User res = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,user.getUsername()));
        if(res != null) {
            return Result.error("-1","用户名已重复");
        }
        userMapper.insert(user);
        return Result.success();
    }

    @CrossOrigin
    @PostMapping("/login")
    public Result<?> login(@RequestBody User user){
        User res = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername,user.getUsername())
                .eq(User::getPassword,user.getPassword()));
        if(res == null) {
            return Result.error("-1","用户名或密码错误");
        }
        String token = TokenUtils.genToken(res);
        res.setToken(token);
        LoginUser loginuser = new LoginUser();
        loginuser.addVisitCount();
        return Result.success(res);
    }

    @PutMapping("/password")
    public Result<?> update(@RequestParam Integer id,
                              @RequestParam String password2){
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id",id);
        User user = new User();
        user.setPassword(password2);
        userMapper.update(user,updateWrapper);
        return Result.success();
    }

    // ==================== 管理员专属接口 ====================

    @PostMapping
    public Result<?> save(@RequestBody User user, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        // 用户名重复校验
        User dup = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, user.getUsername()));
        if (dup != null) {
            return Result.error("1", "用户名已存在，请更换");
        }

        if(user.getPassword() == null){
            user.setPassword("abc123456");
        }
        user.setRole(2);  // 管理员只能新增普通读者
        userMapper.insert(user);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("newUserId", user.getId());
        detail.put("newUsername", user.getUsername());
        detail.put("nickName", user.getNickName());
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "ADD_USER", detail);
        return Result.success();
    }

    @PutMapping
    public Result<?> password(@RequestBody User user, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        Integer requestUserId = (Integer) request.getAttribute("userId");
        // 管理员可编辑任何人，读者只能编辑自己的信息
        if (role == null || (role != 1 && !requestUserId.equals(user.getId()))) {
            return Result.error("403", "无权限操作");
        }

        userMapper.updateById(user);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("editedUserId", user.getId());
        detail.put("nickName", user.getNickName());
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "EDIT_USER", detail);
        return Result.success();
    }

    @PostMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestBody List<Integer> ids, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        userMapper.deleteBatchIds(ids);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("userIds", ids);
        detail.put("count", ids.size());
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_USER", detail);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        // 校验：有未归还图书的用户禁止删除
        LambdaQueryWrapper<BookWithUser> bwQuery = new LambdaQueryWrapper<>();
        bwQuery.eq(BookWithUser::getUserId, id.intValue());
        if (bookWithUserMapper.selectCount(bwQuery) > 0) {
            return Result.error("1", "该读者有未归还图书，请先归还后再删除");
        }

        userMapper.deleteById(id);
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("deletedUserId", id);
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_USER", detail);
        return Result.success();
    }

    @GetMapping("/usersearch")
    public Result<?> findPage2(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search1,
                               @RequestParam(defaultValue = "") String search2,
                               @RequestParam(defaultValue = "") String search3,
                               @RequestParam(defaultValue = "") String search4,
                               HttpServletRequest request){
        Result<?> perm = JwtInterceptor.requireAdmin(request);
        if (perm != null) return perm;

        LambdaQueryWrapper<User> wrappers = Wrappers.<User>lambdaQuery();
        QueryUtils.eqIfNotBlank(wrappers, User::getId, search1);
        QueryUtils.likeIfNotBlank(wrappers, User::getNickName, search2);
        QueryUtils.likeIfNotBlank(wrappers, User::getPhone, search3);
        QueryUtils.likeIfNotBlank(wrappers, User::getAddress, search4);
        wrappers.eq(User::getRole,2);
        Page<User> userPage = userMapper.selectPage(new Page<>(pageNum, pageSize), wrappers);
        return Result.success(userPage);
    }
}
