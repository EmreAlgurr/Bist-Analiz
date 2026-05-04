package com.bist.repository;

import com.bist.entity.HisseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HisseRepository extends JpaRepository<HisseEntity, String> {
}
