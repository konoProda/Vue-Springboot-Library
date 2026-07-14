package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Book;
import com.example.demo.entity.BookWithUser;
import com.example.demo.entity.LendRecord;
import com.example.demo.entity.User;
import com.example.demo.mapper.BookMapper;
import com.example.demo.mapper.BookWithUserMapper;
import com.example.demo.mapper.LendRecordMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.LendRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
public class LendRecordServiceImpl implements LendRecordService {

    @Resource
    private LendRecordMapper lendRecordMapper;

    @Resource
    private BookWithUserMapper bookWithUserMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        LendRecord record = lendRecordMapper.selectById(id);
        if (record == null) return;

        // 若未归还 → 删除活跃借阅 + 恢复库存
        if ("0".equals(record.getStatus())) {
            deleteBookWithUser(record.getIsbn(), record.getReaderId());
            restoreAvailableCopies(record.getIsbn());
        }

        lendRecordMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecords(List<LendRecord> records) {
        for (LendRecord rec : records) {
            deleteById(rec.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLendRecord(Long id, LendRecord lendRecord) {
        LendRecord oldRecord = lendRecordMapper.selectById(id);
        if (oldRecord == null) {
            throw new RuntimeException("借阅记录不存在");
        }

        boolean wasReturned = "1".equals(oldRecord.getStatus());
        boolean nowReturned = "1".equals(lendRecord.getStatus());

        if (!wasReturned && nowReturned) {
            handleNotReturnedToReturned(id, lendRecord, oldRecord);
        } else if (wasReturned && !nowReturned) {
            handleReturnedToNotReturned(id, lendRecord, oldRecord);
        } else {
            handleStatusUnchanged(id, lendRecord);
        }
    }

    // ==================== 状态变更子方法 ====================

    private void handleNotReturnedToReturned(Long id, LendRecord lendRecord, LendRecord oldRecord) {
        if (lendRecord.getReturnTime() == null) {
            lendRecord.setReturnTime(new Date());
        }
        lendRecordMapper.updateById(lendRecord);

        // 删除 bookwithuser 活跃记录
        deleteBookWithUser(oldRecord.getIsbn(), oldRecord.getReaderId());

        // 恢复 book.availableCopies
        restoreAvailableCopies(oldRecord.getIsbn());
    }

    private void handleReturnedToNotReturned(Long id, LendRecord lendRecord, LendRecord oldRecord) {
        lendRecord.setReturnTime(null);
        lendRecord.setStatus("0");
        lendRecordMapper.updateById(lendRecord);

        // 检查并扣减库存
        String isbn = oldRecord.getIsbn();
        LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
        bookQuery.eq(Book::getIsbn, isbn);
        Book book = bookMapper.selectOne(bookQuery);
        if (book == null || book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
            throw new RuntimeException("库存不足，无法恢复为未归还状态");
        }
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        int updated = bookMapper.updateById(book);
        if (updated == 0) {
            throw new RuntimeException("操作失败：图书信息已被其他操作修改，请刷新后重试");
        }

        // 创建 bookwithuser 活跃记录（避免重复）
        recreateBookWithUser(isbn, oldRecord.getReaderId(), oldRecord);
    }

    private void handleStatusUnchanged(Long id, LendRecord lendRecord) {
        lendRecordMapper.updateById(lendRecord);
    }

    // ==================== 辅助方法 ====================

    private void deleteBookWithUser(String isbn, Integer readerId) {
        Map<String, Object> deleteMap = new HashMap<>();
        deleteMap.put("isbn", isbn);
        deleteMap.put("user_id", readerId);
        bookWithUserMapper.deleteByMap(deleteMap);
    }

    private void restoreAvailableCopies(String isbn) {
        LambdaQueryWrapper<Book> bookQuery = new LambdaQueryWrapper<>();
        bookQuery.eq(Book::getIsbn, isbn);
        Book book = bookMapper.selectOne(bookQuery);
        if (book != null) {
            book.setAvailableCopies(book.getAvailableCopies() != null
                    ? book.getAvailableCopies() + 1 : 1);
            int updated = bookMapper.updateById(book);
            if (updated == 0) {
                throw new RuntimeException("操作失败：图书信息已被其他操作修改，请刷新后重试");
            }
        }
    }

    private void recreateBookWithUser(String isbn, Integer readerId, LendRecord oldRecord) {
        LambdaQueryWrapper<BookWithUser> existCheck = new LambdaQueryWrapper<>();
        existCheck.eq(BookWithUser::getUserId, readerId)
                  .eq(BookWithUser::getIsbn, isbn);
        if (bookWithUserMapper.selectCount(existCheck) > 0) return;

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
