package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.PlanCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlanCategoryRepository : JpaRepository<PlanCategory, Long>
