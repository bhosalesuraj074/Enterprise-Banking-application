package com.key.deposite.repository;

import com.key.deposite.entity.DepositHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepositHoldRepository extends JpaRepository<DepositHold, UUID> {
    List<DepositHold> findByAccountIdAndStatus(String accountId, String status);

}
