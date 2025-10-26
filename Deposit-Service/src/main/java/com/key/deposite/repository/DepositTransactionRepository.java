package com.key.deposite.repository;

import com.key.deposite.entity.DepositTransaction;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DepositTransactionRepository extends JpaRepository<DepositTransaction, UUID> {

    List<DepositTransaction> findByAccountIdAndPostedAtAfterOrderByPostedAtDesc(String accountId, LocalDateTime fromDate);

    @Query("SELECT t FROM DepositTransaction t WHERE t.accountId = :accountId ORDER BY t.postedAt DESC")
    List<DepositTransaction> findRecentByAccountId(@Param("accountId") String accountId);
}
