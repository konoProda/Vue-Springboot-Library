package com.example.demo.service;

import cn.hutool.json.JSONUtil;
import com.example.demo.entity.OperationLog;
import com.example.demo.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Map;

/**
 * 操作日志服务。
 * 使用 @Async 异步写入，日志失败不影响主业务事务。
 */
@Slf4j
@Service
public class OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    /**
     * 异步记录操作日志。
     *
     * @param userId        操作用户 ID
     * @param username      操作用户名
     * @param userRole      操作人角色 (1=管理员, 2=读者)
     * @param operationType 操作类型 (BORROW / RETURN / RENEW / DELETE_BOOK / DELETE_USER ...)
     * @param detail        操作详情 (Map → JSON)
     */
    @Async
    public void log(Long userId, String username, Integer userRole, String operationType, Map<String, Object> detail) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId);
            logEntry.setUsername(username);
            logEntry.setUserRole(userRole);
            logEntry.setOperationType(operationType);
            logEntry.setDetail(detail != null ? JSONUtil.toJsonStr(detail) : null);
            logEntry.setCreateTime(new Date());
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志记录失败不能影响主业务
            log.error("写入操作日志失败: type={}, userId={}", operationType, userId, e);
        }
    }
}
