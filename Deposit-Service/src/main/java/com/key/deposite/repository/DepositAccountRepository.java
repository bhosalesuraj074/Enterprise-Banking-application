package com.key.deposite.repository;

import com.key.deposite.entity.DepositAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface DepositAccountRepository extends JpaRepository<DepositAccount, UUID> {
    Optional<DepositAccount> findByAccountIdAndIsDeletedFalse(String accountId);
}
