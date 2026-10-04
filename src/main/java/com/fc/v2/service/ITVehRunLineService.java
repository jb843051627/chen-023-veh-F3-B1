package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;

/**
 * 运行界次判定线 Service接口。
 *
 * 界次归层四问（按哪条界归层、一条界都够不上时给不给说法、交班次序相同听谁的、
 * 报来的数折不折算往哪头折）只由本服务 {@link #verdict} 一处作答；
 * 屏上不得另辟第二个能出这个结果的入口。
 *
 * 旧办法（凭界次代号取值的 evaluate / evaluateTop）照原样摆着，不往它身上挂新参数；
 * 这一回的新走法一律另起名目（verdict / listEffective / browseRegister / inspectRegister /
 * danglingScreenCodes）。
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
     * 按主键查询规则（旧办法，原样保留）。
     */
    TVehRunLine selectTVehRunLineById(Long id);

    /**
     * 【旧办法】单规则求值：按 ruleCode 取界判档（1..4）。
     * 凭界次代号取值的旧走法，照原样摆着，不挂新参数、不接新口径。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /**
     * 【旧办法】多规则求值（旧口径）。无可用规则返回 0。
     */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 【旧办法】在册界清单（旧口径：只排除留痕删除，不做生效窗口）。
     */
    List<TVehRunLine> listAvailable(Date at);

    /**
     * 【旧办法】单条规则是否可用（旧口径）。
     */
    boolean usable(Long id, Date at);

    /**
     * 【旧办法】在册界条数（旧口径）。
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
     * @param measureKind 所量类别（speed 车速 / duration 连续驾驶时长）
     * @param rawValue    车上报回来的数（车速 m/s、时长分钟）
     * @param at          问话的那一刻（补判旧账就传旧账自己的那一刻，不许拿新界顶替）
     * @return 归层结论；measureKind/rawValue/at 缺一返回 null（由调用方按缺参回绝）
     */
    RunLineVerdict verdict(String measureKind, BigDecimal rawValue, Date at);

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
