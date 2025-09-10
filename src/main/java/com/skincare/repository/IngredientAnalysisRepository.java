package com.skincare.repository;

import com.skincare.model.IngredientAnalysis;
import com.skincare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientAnalysisRepository extends JpaRepository<IngredientAnalysis, Long> {
    List<IngredientAnalysis> findByUserOrderByAnalysisDateDesc(User user);
}
