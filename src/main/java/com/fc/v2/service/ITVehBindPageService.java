package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TVehBindPage;
import com.fc.v2.model.auto.TVehEnt;
import com.fc.v2.model.custom.VehBindBoard;
import com.fc.v2.model.custom.VehBindForm;
import com.fc.v2.model.custom.VehBindQuery;

import java.util.List;

/**
 * 车辆联网清单页 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITVehBindPageService {

    /** 联网进展：新录 */
    int STATUS_NEW = 0;
    /** 联网进展：已联齐 */
    int STATUS_LINKED = 1;
    /** 联网进展：缺项 */
    int STATUS_LACK = 2;

    /** 页所在层级：县域 */
    int NODE_COUNTY = 0;
    /** 页所在层级：市域 */
    int NODE_CITY = 1;
    /** 页所在层级：省域 */
    int NODE_PROVINCE = 2;

    /** 按主键查询 */
    TVehBindPage selectTVehBindPageById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TVehBindPage> selectTVehBindPageList(Wrapper<TVehBindPage> queryWrapper);

    /**
     * 立页（一车一张亲手立）——业务服务层唯一的写入判路，参数表与返回保持不动。
     * 立不立得住、还差几项轧出几，全在这一条里作答：
     * 业户先对总名录（代号对不上的页起不来，已销户的不许再立新页）；
     * 清单代号、随页签认码由系统排号，本子自带号码一概不认；
     * 还差几项谁都不填，一律按 应联-已联 重轧覆盖，进展随轧出的数定为新录/已联齐。
     * 过不了库的抛 VehBindRejectException，退回时点明毛病出在哪一格。
     * 落成返回 1。
     */
    int insertTVehBindPage(TVehBindPage record);

    /**
     * 改页——已联项数改口、页换挂靠那一层三种时候都在此重轧一遍，
     * 轧出的新数覆盖旧数；清单代号、签认码、业户、车辆代号只照旧不许改。
     * 参数表与返回保持不动；过不了库的抛 VehBindRejectException 并点明格子。
     */
    int updateTVehBindPage(TVehBindPage record);

    /**
     * 从当月核收里带出立页（立页第二条路子）。
     * 与亲手立走的是同一套数法、同一道判路，界面上也只有一种样子；
     * 只是另起一个同义方法接住，不动原写入方法的参数表与返回。
     */
    int bringFromMonthlyCheck(VehBindForm form);

    /**
     * 接页面表单的落页口子：应联、已联按原文收，空着/带零头在此点明格子退回，
     * 数法、名录、销户、轧差一律转去那唯一一条写入方法作答，接口层不留第二处判路。
     * 立页与改页按 id 有无同走这一口；返回影响行数。
     */
    int saveFromForm(VehBindForm form);

    /**
     * 按属地那一路开屏：页头三栏（挂页车数、已联齐、缺项）与名下各页
     * 出自同一回点算、同一个口径；县岗不见邻县、市域不掺别市、省域只管本省。
     * 本层没立过页时三栏皆 0、名单空着，绝不从邻处借行来撑；
     * 已销户业户此前立过的页照旧列得出、照旧算在数内。
     */
    VehBindBoard openBoard(VehBindQuery query);

    /**
     * 立页选业户用：只给本地属地这一路上、仍在册的业户（已销户的不许选来立新页），
     * 道路旅客运输、危险货物运输、普通货运三类各排各的序。
     */
    List<TVehEnt> listRoster(String roadPrefix);

    /** 按总名录 id 回查业户（已销户的也照查——它名下旧页改看时仍要显出全称） */
    TVehEnt selectEntById(Integer siteId);

    /** 批量删除（留痕删除，不抹库里实有的页） */
    int deleteTVehBindPageByIds(String ids);

    /** 按主键删除（留痕删除） */
    int deleteTVehBindPageById(Long id);

    /**
     * 推进一态（待核对 → 已核对 → 已冻住）。
     * 清单页的进展按三态推进；已冻住的页不再往前推。
     * 参数表与返回值以本行为准，实现方需给出真实推进逻辑。
     */
    default int advanceTVehBindPage(Long id) {
        throw new UnsupportedOperationException("推进一态的逻辑尚未实现");
    }

    /**
     * 退回一态（已核对 → 待核对，已冻住 → 已核对）；旧记录沉底留痕。
     * 参数表与返回值以本行为准，实现方需给出真实退回逻辑。
     */
    default int revertTVehBindPage(Long id) {
        throw new UnsupportedOperationException("退回一态的逻辑尚未实现");
    }
}
