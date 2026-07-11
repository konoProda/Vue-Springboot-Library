package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.LoginUser;
import com.example.demo.commom.Result;
import com.example.demo.entity.BookWithUser;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.OperationLogService;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.Null;
import org.springframework.web.bind.annotation.*;
import com.example.demo.utils.TokenUtils;


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
    OperationLogService operationLogService;

    // ==================== 公开接口（无需管理员权限） ====================

    @PostMapping("/register")
    public Result<?> register(@RequestBody User user){
        User res = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,user.getUsername()));
        if(res != null)
        {
            return Result.error("-1","用户名已重复");
        }
        userMapper.insert(user);
        return Result.success();
    }

    @CrossOrigin
    @PostMapping("/login")
    public Result<?> login(@RequestBody User user){
        User res = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,user.getUsername()).eq(User::getPassword,user.getPassword()));
        if(res == null)
        {
            return Result.error("-1","用户名或密码错误");
        }
        String token = TokenUtils.genToken(res);
        res.setToken(token);
        LoginUser loginuser = new LoginUser();
        loginuser.addVisitCount();
        return Result.success(res);
    }

    /** 修改自己的密码（任何已登录用户均可操作） */
    @PutMapping("/password")
    public  Result<?> update( @RequestParam Integer id,
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
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        if(user.getPassword() == null){
            user.setPassword("abc123456");
        }
        userMapper.insert(user);
        return Result.success();
    }

    /** 管理员修改用户信息 */
    @PutMapping
    public  Result<?> password(@RequestBody User user, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        userMapper.updateById(user);
        return Result.success();
    }

    @PostMapping("/deleteBatch")
    public  Result<?> deleteBatch(@RequestBody List<Integer> ids, HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        userMapper.deleteBatchIds(ids);
        // 记录操作日志
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
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        userMapper.deleteById(id);
        // 记录操作日志
        Integer userId = (Integer) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Map<String, Object> detail = new HashMap<>();
        detail.put("deletedUserId", id);
        operationLogService.log(userId != null ? userId.longValue() : null,
                username != null ? username : "", 1, "DELETE_USER", detail);
        return Result.success();
    }

    @GetMapping
    public Result<?> findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search,
                              HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        LambdaQueryWrapper<User> wrappers = Wrappers.<User>lambdaQuery();
        if(StringUtils.isNotBlank(search)){
            wrappers.like(User::getNickName,search);
        }
        wrappers.like(User::getRole,2);
        Page<User> userPage =userMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(userPage);
    }

    @GetMapping("/usersearch")
    public Result<?> findPage2(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String search1,
                               @RequestParam(defaultValue = "") String search2,
                               @RequestParam(defaultValue = "") String search3,
                               @RequestParam(defaultValue = "") String search4,
                               HttpServletRequest request){
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        LambdaQueryWrapper<User> wrappers = Wrappers.<User>lambdaQuery();
        if(StringUtils.isNotBlank(search1)){
            wrappers.like(User::getId,search1);
        }
        if(StringUtils.isNotBlank(search2)){
            wrappers.like(User::getNickName,search2);
        }
        if(StringUtils.isNotBlank(search3)){
            wrappers.like(User::getPhone,search3);
        }
        if(StringUtils.isNotBlank(search4)){
            wrappers.like(User::getAddress,search4);
        }
        wrappers.like(User::getRole,2);
        Page<User> userPage =userMapper.selectPage(new Page<>(pageNum,pageSize), wrappers);
        return Result.success(userPage);
    }
}
