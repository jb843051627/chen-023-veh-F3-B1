package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TVehBindPage;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 联网清单一屏：页头三栏（挂页车数、已联齐、缺项）与名下各页是同一次算的产物。
 * 本层这一季没立过页时三栏皆 0、名单为空——不许从邻处借行来撑。
 *
 * @author fuce
 */
public class VehBindBoard implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页所在层级序 0县域 1市域 2省域 */
    private Integer nodeNo;

    /** 挂页的车数（本屏实有页数，一张一张点出来的那个数） */
    private int totalCount;

    /** 已联齐的车数（进展=已联齐） */
    private int linkedCount;

    /** 缺项的车数（进展=缺项） */
    private int lackCount;

    /** 名下各页（与上面三个数同一 WHERE 片段同一回点算） */
    private List<TVehBindPage> pages = Collections.emptyList();

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getLinkedCount() {
        return linkedCount;
    }

    public void setLinkedCount(int linkedCount) {
        this.linkedCount = linkedCount;
    }

    public int getLackCount() {
        return lackCount;
    }

    public void setLackCount(int lackCount) {
        this.lackCount = lackCount;
    }

    public List<TVehBindPage> getPages() {
        return pages;
    }

    public void setPages(List<TVehBindPage> pages) {
        this.pages = pages;
    }
}
