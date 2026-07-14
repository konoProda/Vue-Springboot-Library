package com.example.demo.service;

import com.example.demo.entity.LendRecord;

import java.util.List;

/**
 * 借阅记录 Service 层，管理借阅记录的增删改及多表同步操作。
 */
public interface LendRecordService {

    /**
     * 按 ID 删除借阅记录，若未归还会同步清理 bookwithuser 并恢复库存。
     */
    void deleteById(Long id);

    /**
     * 批量删除借阅记录。
     */
    void deleteRecords(List<LendRecord> records);

    /**
     * 更新借阅记录，支持归还/取消归还状态变更时的跨表同步。
     */
    void updateLendRecord(Long id, LendRecord lendRecord);
}
