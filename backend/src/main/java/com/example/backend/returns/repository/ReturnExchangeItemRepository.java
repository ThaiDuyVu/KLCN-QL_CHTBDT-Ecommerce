package com.example.backend.returns.repository;

import com.example.backend.returns.entity.ReturnExchangeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReturnExchangeItemRepository extends JpaRepository<ReturnExchangeItem, UUID> {
    List<ReturnExchangeItem> findByReturnIdIn(Collection<UUID> returnIds);
}
