package com.fc.v2.mapper.custom;

import com.fc.v2.model.auto.TVehBindPage;
import com.fc.v2.model.custom.VehBindQuery;

import java.util.List;

/**
 * 车辆联网清单屏查数据层。
 * 名单与页头三栏在同一条 SQL、同一回点算里出（窗口计数），不许拆成两本账。
 *
 * @author fuce
 */
public interface TVehBindPageBoardMapper {

    /**
     * 按层级与属地那一路开屏：分页名单上自带本屏的挂页车数、已联齐、缺项三个窗口计数。
     */
    List<TVehBindPage> selectBoardPage(VehBindQuery query);
}
