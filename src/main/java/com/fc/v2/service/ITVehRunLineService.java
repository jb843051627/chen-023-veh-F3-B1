package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.auto.TVehRunVerdict;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;

/**
 * 运行界次判定线 Service接口。
 *
 * 界次归层四问（按哪条界归层、一条界都够不上时给不给说法、交班次序相同听谁的、
 * 报来的数折不折算往哪头折）只由本服务 {@link #verdict} 一处作答；
 * 屏上不得另辟第二个能出这个结果的入口。
 *
 * 旧名目（evaluate / evaluateTop / listAvailable / usable / countAvailable）留着给老调用方，
 * 签名一个不动；内里与新走法同一把尺——压线归高一档、那一刻在位、停用不参与、
 * 次序相同按说话次序、量程越界回兜底——新老两处一个答复，不再各说各的。
 *
 * 每一次裁决判完即归档在册（t_veh_run_verdict），日后追溯以归档这一份为准（
 * {@link #listVerdictArchive} 只翻档不重算）。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITVehRunLineService {

    /** 层序：提示 */
    int LEVEL_TIP = 1;
    /** 层序：严管 */
    int LEVEL_STRICT = 2;
    /** 层序：立案 */
    int LEVEL_CASE = 3;

    /** 结论栏：一条界都够不上 */
    String TEXT_NO_RULE = "无可用界";
    /** 结论栏：在位界请得出，但报来的数还没到起算 */
    String TEXT_BELOW_START = "未到起算";

    /** 所量类别：车速（车上报每秒多少米，界册记每小时多少公里） */
    String MEASURE_SPEED = "speed";
    /** 所量类别：连续驾驶时长（车上报多少分钟，界册记小时） */
    String MEASURE_DURATION = "duration";

    /**
     * 按主键查询规则（旧名目，原样保留）。
     */
    TVehRunLine selectTVehRunLineById(Long id);

    /**
     * 【旧名目】单规则求值：按 ruleCode 取那一刻的在位界判档（1..4）。
     * 签名不动的老名目，内里与新走法同一把尺：历史时点用历史界（不许最新版顶替）、
     * 停用与站不住的界不参与、压线归高一档、量程（0..100，界册那一头的数）越界回 0。
     * 无在位版本 / 输入越界 / 参数缺失一律返回 0（兜底值，不抛异常）。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 【旧名目】多规则求值：取那一刻说话的那条界判档（1..4）。
     * 名单与 {@link #listAvailable} 同源、同一把说话次序，同一刻问两遍同一个答复。
     * 无可用规则 / 输入越界 / 参数缺失返回 0。
     */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 【旧名目】在册界清单：与新走法同一处筛选（未停用、分界数站得住、那一刻在位）、
     * 同一把说话次序；老名目不分所量类别，两条线的界都在单上。
     */
    List<TVehRunLine> listAvailable(Date at);

    /**
     * 【旧名目】单条规则那一刻可用不可用以否：在册、未停用、站得住、那一刻在位，
     * 口径与新走法同出一处。
     */
    boolean usable(Long id, Date at);

    /**
     * 【旧名目】在册界条数：与 {@link #listAvailable} 同源，不另点一本账。
     */
    int countAvailable(Date at);

    /**
     * 【新·唯一裁决口】界次归层四问只此一处作答。
     *
     * 走法只有一套：
     * 1. 取数：车上报的数先折到界册那一头（车速 m/s×3.6 折成 km/h；时长分钟÷60 折成小时），
     *    取数与比数同一把舍法（HALF_UP，与界册列同到三位小数），不许多处各舍各的；
     * 2. 请界：只认问话那一刻在位的界——起用那一日已经够着（含当日）、交班那一日还没到（不含当日）；
     *    交班栏空着的存量界认作一直在位；已停用的不参与；分界数缺格/没越过前头那格的界站不住，不参与；
     * 3. 说话：在位凑出两条时先比交班次序（priority 大者说话），次序相同再比界次代号，
     *    照排法落在末位（代号字典序最大）的胜出，同一刻只许一个答复；
     * 4. 归层：三层只有一套说法，压线归本层——[起算,够线)提示、[够线,封顶)严管、[封顶及以上)立案；
     *    四条界一条都够不上时不给层，结论写"无可用界"，界次代号与起用那一日两栏一并留空。
     *
     * 量程校验只此一处：负数一律越界，车速过 70 m/s、时长过 1440 分钟视为采坏，
     * 越界的数不判档，与缺参同一路回绝（返回 null）。
     *
     * 每一次裁决判完即归档在册（t_veh_run_verdict），日后追溯以归档这一份为准。
     *
     * @param measureKind 所量类别（speed 车速 / duration 连续驾驶时长）
     * @param rawValue    车上报回来的数（车速 m/s、时长分钟）
     * @param at          问话的那一刻（补判旧账就传旧账自己的那一刻，不许拿新界顶替）
     * @return 归层结论；measureKind/rawValue/at 缺一或越出量程返回 null（由调用方按缺参回绝）
     */
    RunLineVerdict verdict(String measureKind, BigDecimal rawValue, Date at);

    /**
     * 【新·追溯只翻档】翻归层归档册：每一次裁决落的那一份，按问话那一刻倒序。
     * 只翻档不重算——日后追溯以这一份为准，不许拿眼下最新界再判一遍顶替。
     * measureKind 传 null 两类都翻；from/to 圈问话时段，可空。
     */
    List<TVehRunVerdict> listVerdictArchive(String measureKind, Date from, Date to);

    /**
     * 【新】把界册逐条走一遍，凑出那一刻、那一类别的在位界，按裁决序排好
     * （交班次序降序，相同再按界次代号降序落到末位）。
     * 屏上那份在位名单与裁决请界出自同一处，不许两本账。
     * 界的情形为"已停用"的、已交班的、还没起用的、留痕删除的，都不在这份名单上。
     */
    List<TVehRunLine> listEffective(String measureKind, Date at);

    /**
     * 【新·查界另起名目】翻检界册：含已停用、已交班的旧界，供翻阅；
     * 这条路只许看，翻得着的旧界不许捞去比数，裁决只认 {@link #verdict}。
     */
    List<TVehRunLine> browseRegister(String measureKind);

    /**
     * 【新】界册体检：分界数某一格空着、后一格没越过前头那一格的，一条条指名道姓报出来，
     * 不许只回一句"数值不对劲"。
     */
    List<RunLineDefect> inspectRegister(String measureKind);

    /**
     * 【新】屏上挂着的界次代号，逐个回册里点：那一条线（所量类别）、那一刻请不出在位界的
     * （无此号/已停用/已交班/还没起用/留痕删除/只是别条线上的界），一条条指出来，附具体缘故。
     * measureKind 传 null 时不分类别点（跨线核对用）。
     */
    List<RunLineDefect> danglingScreenCodes(String measureKind, Collection<String> screenCodes, Date at);
}
