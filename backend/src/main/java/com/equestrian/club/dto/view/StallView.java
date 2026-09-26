package com.equestrian.club.dto.view;

import java.math.BigDecimal;

/** 栏位视图：附带当前占用马匹的编号与姓名 */
public record StallView(
        Long id,
        String stallNo,
        String barnName,
        BigDecimal areaSqm,
        String status,
        String statusName,
        Long horseId,
        String horseNo,
        String horseName) {
}
