package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.conf.V2Config;
import com.fc.v2.common.exception.VehBindRejectException;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TVehBindPageMapper;
import com.fc.v2.mapper.auto.TVehEntMapper;
import com.fc.v2.mapper.custom.TVehBindPageBoardMapper;
import com.fc.v2.model.auto.TVehBindPage;
import com.fc.v2.model.auto.TVehEnt;
import com.fc.v2.model.custom.VehBindBoard;
import com.fc.v2.model.custom.VehBindForm;
import com.fc.v2.model.custom.VehBindQuery;
import com.fc.v2.service.ITVehBindPageService;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 车辆联网清单页Service业务层处理
 *
 * 立不立得住、还差几项轧出几、按属地该见哪几页、页头三数打哪儿来，
 * 四样全归这一条写入/开屏路径作答；接口层与页面上不留第二处能绕过它的判路。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TVehBindPageServiceImpl extends ServiceImpl<TVehBindPageMapper, TVehBindPage>
        implements ITVehBindPageService {

    /** 每屏几条的兜底（处里定的条数缺省时） */
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    private TVehEntMapper vehEntMapper;

    @Autowired
    private TVehBindPageBoardMapper boardMapper;

    @Autowired
    private V2Config v2Config;

    @Override
    public TVehBindPage selectTVehBindPageById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TVehBindPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TVehBindPage> selectTVehBindPageList(Wrapper<TVehBindPage> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    /**
     * 立页（一车一张亲手立）——唯一的写入判路，参数表与返回保持不动。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTVehBindPage(TVehBindPage record) {
        if (record == null) {
            throw new VehBindRejectException("form", "空页递进来，落不进库");
        }
        prepareAndValidate(record);

        // 清单代号、随页签认码两样由系统排号；本子上自带号码递来一概不认
        record.setId(null);
        record.setBillNo("QD" + SnowflakeIdWorker.getUUID());
        record.setSignNo("QR" + SnowflakeIdWorker.getUUID());

        // 头一回立页也要轧一遍；立页那一刻进展记"新录"，联齐/缺项等改口、换层时随轧出的数定
        record.setLackCount(record.getShouldCount() - record.getDoneCount());
        record.setStatus(STATUS_NEW);
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    /**
     * 从当月核收里带出立页：另起的同义方法，接住的还是同一套数法、同一道判路。
     * 只接立页（无 id），改口、换层不走这条。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bringFromMonthlyCheck(VehBindForm form) {
        if (form != null) {
            form.setId(null);
        }
        return saveFromForm(form);
    }

    /**
     * 页面表单落页口子：两格数目按原文收，空/零头点明格子退回，其余判路全转唯一写入方法。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveFromForm(VehBindForm form) {
        if (form == null) {
            throw new VehBindRejectException("form", "空页递进来，落不进库");
        }
        TVehBindPage record = new TVehBindPage();
        record.setId(form.getId());
        record.setVehCode(form.getVehCode());
        record.setSiteId(form.getSiteId());
        record.setSiteNo(form.getSiteNo());
        record.setNodeNo(form.getNodeNo());
        record.setContent(form.getContent());
        record.setRemark(form.getRemark());
        record.setShouldCount(parseCount(form.getShouldCountRaw(), "shouldCount"));
        record.setDoneCount(parseCount(form.getDoneCountRaw(), "doneCount"));

        // 有 id 走改口/换层，无 id 走立页，数法是同一套
        return form.getId() == null ? insertTVehBindPage(record) : updateTVehBindPage(record);
    }

    /**
     * 两格数目原文入册：空着按空递退回，带零头（小数、念不成整数的）按零头退回，
     * 负数先放行进唯一判路由那儿点明。
     */
    private Integer parseCount(String raw, String field) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            // null 交给后面的唯一判路，由它说"空着递"
            return null;
        }
        if (!text.matches("-?\\d+")) {
            throw new VehBindRejectException(field, "这一格递进来带零头，只收整数项数，这一页落不进库");
        }
        try {
            return Integer.valueOf(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw new VehBindRejectException(field, "这一格的数目念不出，这一页落不进库");
        }
    }

    /**
     * 改页：已联项数改口、页换挂靠那一层都在此重轧一遍，轧出的新数覆盖旧数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTVehBindPage(TVehBindPage record) {
        if (record == null || record.getId() == null) {
            throw new VehBindRejectException("id", "没点到库里实有的页，改不了");
        }
        TVehBindPage old = selectTVehBindPageById(record.getId());
        if (old == null) {
            throw new VehBindRejectException("id", "这一页在库里寻不着，改不了");
        }

        // 清单代号、签认码、所属业户、车辆代号只照旧，递来改值的一概不认
        record.setBillNo(null);
        record.setSignNo(null);
        record.setSiteId(null);
        record.setSiteNo(null);
        record.setVehCode(null);

        // 改口的用新数，没递到的照旧数，再走同一套数法重轧
        if (record.getShouldCount() == null) {
            record.setShouldCount(old.getShouldCount());
        }
        if (record.getDoneCount() == null) {
            record.setDoneCount(old.getDoneCount());
        }
        if (record.getNodeNo() == null) {
            record.setNodeNo(old.getNodeNo());
        }
        validateNumbersAndLayer(record);

        TVehBindPage upd = new TVehBindPage();
        upd.setId(old.getId());
        upd.setNodeNo(record.getNodeNo());
        upd.setShouldCount(record.getShouldCount());
        upd.setDoneCount(record.getDoneCount());
        // 轧出来的新数覆盖旧数，这一格只回显
        upd.setLackCount(record.getShouldCount() - record.getDoneCount());
        // 改口、换层这两种重轧时候，进展随轧出的数定：两数齐平即已联齐，还差即缺项
        upd.setStatus(upd.getLackCount() != null && upd.getLackCount() == 0 ? STATUS_LINKED : STATUS_LACK);
        upd.setContent(record.getContent());
        upd.setRemark(record.getRemark());
        return this.baseMapper.update(upd, new UpdateWrapper<TVehBindPage>()
                .eq("id", old.getId())
                .eq("del_flag", 0));
    }

    /**
     * 立页前的同一套数法：名录、销户、一车一页、层级与两格数目逐项过，
     * 过不了的当场点明毛病出在哪一格。
     *
     * @param record 待落页
     */
    private void prepareAndValidate(TVehBindPage record) {
        String vehCode = record.getVehCode() == null ? null : record.getVehCode().trim();
        if (StringUtils.isEmpty(vehCode)) {
            throw new VehBindRejectException("vehCode", "车辆营运代号空着，对不上车，这一页起不来");
        }
        record.setVehCode(vehCode);

        validateNumbersAndLayer(record);

        if (record.getSiteId() == null) {
            throw new VehBindRejectException("siteId", "所属业户空着，先认总名录再立页");
        }
        // 页认不认，先看所属业户在不在总名录；代号不统一名录的，页起不来
        TVehEnt ent = vehEntMapper.selectOne(new QueryWrapper<TVehEnt>()
                .eq("id", record.getSiteId())
                .eq("del_flag", 0));
        if (ent == null) {
            throw new VehBindRejectException("siteId", "所属业户不在总名录，代号对不上，这一页起不来");
        }
        if (StringUtils.isNotEmpty(record.getSiteNo()) && !ent.getSiteNo().equals(record.getSiteNo().trim())) {
            throw new VehBindRejectException("siteNo", "业户代号与总名录对不上，这一页起不来");
        }
        // 已经销户的业户，名下不许再立新页（它此前立过的页不由此抹去）
        if (ent.getStatus() != null && ent.getStatus() == 1) {
            throw new VehBindRejectException("siteId", "该业户已销户，名下不许再立新页");
        }
        record.setSiteId(Integer.valueOf(ent.getId().intValue()));
        record.setSiteNo(ent.getSiteNo());

        // 一辆车一张联网清单：同车已有在册页的，第二张不许再立
        Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TVehBindPage>()
                .eq("veh_code", vehCode)
                .eq("del_flag", 0));
        if (dupCnt != null && dupCnt > 0) {
            throw new VehBindRejectException("vehCode", "这辆车已经立过页，一车一张，不许重复立页");
        }
    }

    /**
     * 层级与两格数目的同一套数法，立页、改口、换层都过这一道。
     */
    private void validateNumbersAndLayer(TVehBindPage record) {
        Integer node = record.getNodeNo();
        if (node == null || (node != NODE_COUNTY && node != NODE_CITY && node != NODE_PROVINCE)) {
            throw new VehBindRejectException("nodeNo", "页所在层级只认县域、市域、省域三层");
        }
        Integer should = record.getShouldCount();
        Integer done = record.getDoneCount();
        if (should == null) {
            throw new VehBindRejectException("shouldCount", "应联项数空着递，这一页落不进库");
        }
        if (should < 0) {
            throw new VehBindRejectException("shouldCount", "应联项数递进来是负数，这一页落不进库");
        }
        if (done == null) {
            throw new VehBindRejectException("doneCount", "已联项数空着递，这一页落不进库");
        }
        if (done < 0) {
            throw new VehBindRejectException("doneCount", "已联项数递进来是负数，这一页落不进库");
        }
        // 应联低过同页已联、已联往上冒过应联，说的是同一处毛病
        if (done > should) {
            throw new VehBindRejectException("doneCount", "已联项数冒过应联项数（应联低过已联），这一页收不进来");
        }
    }

    @Override
    public VehBindBoard openBoard(VehBindQuery query) {
        if (query == null || query.getNodeNo() == null
                || (query.getNodeNo() != NODE_COUNTY
                && query.getNodeNo() != NODE_CITY
                && query.getNodeNo() != NODE_PROVINCE)) {
            throw new VehBindRejectException("nodeNo", "开屏先报层级：县域、市域、省域三层各管自家那一片");
        }
        if (StringUtils.isEmpty(query.getRoadPrefix())) {
            throw new VehBindRejectException("roadPrefix", "属地一直写到县，不报属地开不了本层那一屏");
        }

        int pageNum = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getLimit() != null && query.getLimit() > 0
                ? query.getLimit()
                : (v2Config.getVehBindPageSize() != null && v2Config.getVehBindPageSize() > 0
                        ? v2Config.getVehBindPageSize() : DEFAULT_PAGE_SIZE);

        // 分页切的是同一条 SQL 的同一批结果：页头三栏是这批结果上的窗口计数，
        // 与一张一张点出来的页同一次算成；没页时窗口计数不回，三栏照零摆、名单空着
        PageHelper.startPage(pageNum, pageSize, false);
        List<TVehBindPage> rows = boardMapper.selectBoardPage(query);
        PageInfo<TVehBindPage> page = new PageInfo<TVehBindPage>(rows);

        VehBindBoard board = new VehBindBoard();
        board.setNodeNo(query.getNodeNo());
        board.setPages(page.getList());
        if (page.getList().isEmpty()) {
            // 本层没立过页就是三栏零，绝不从邻处借几行来撑
            board.setTotalCount(0);
            board.setLinkedCount(0);
            board.setLackCount(0);
        } else {
            TVehBindPage first = page.getList().get(0);
            board.setTotalCount(first.getBoardTotal() != null ? first.getBoardTotal() : (int) page.getTotal());
            board.setLinkedCount(first.getBoardLinked() != null ? first.getBoardLinked() : 0);
            board.setLackCount(first.getBoardLack() != null ? first.getBoardLack() : 0);
        }
        return board;
    }

    @Override
    public List<TVehEnt> listRoster(String roadPrefix) {
        QueryWrapper<TVehEnt> wrapper = new QueryWrapper<TVehEnt>()
                .eq("del_flag", 0)
                .eq("status", 0);
        if (StringUtils.isNotEmpty(roadPrefix)) {
            wrapper.likeRight("road_name", roadPrefix);
        }
        // 道路旅客运输、危险货物运输、普通货运三类各排各的序，类内再按代号排
        wrapper.orderByAsc("site_type", "site_no");
        return vehEntMapper.selectList(wrapper);
    }

    @Override
    public TVehEnt selectEntById(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        return vehEntMapper.selectOne(new QueryWrapper<TVehEnt>()
                .eq("id", siteId)
                .eq("del_flag", 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTVehBindPageByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        // 留痕删除：在场的页不许被顺手抹掉，只打删除标记；旧账照旧留底
        TVehBindPage upd = new TVehBindPage();
        upd.setDelFlag(1);
        return this.baseMapper.update(upd, new UpdateWrapper<TVehBindPage>()
                .in("id", new ArrayList<Long>(Arrays.asList(idArr)))
                .eq("del_flag", 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTVehBindPageById(Long id) {
        TVehBindPage upd = new TVehBindPage();
        upd.setId(id);
        upd.setDelFlag(1);
        return this.baseMapper.update(upd, new UpdateWrapper<TVehBindPage>()
                .eq("id", id)
                .eq("del_flag", 0));
    }
}
