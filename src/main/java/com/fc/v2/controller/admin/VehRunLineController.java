package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.auto.TVehRunLineVerdictRecord;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;
import com.fc.v2.service.ITVehRunLineService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 运行界次判定 Controller。
 *
 * 归层只有这一道门（/verdict）：按哪条界归层、一条界都够不上时给什么说法、
 * 交班次序相同听谁的、报来的数折不折算又往哪一头折——四问只由服务层那一处作答，
 * 本控制器只递话，不判一个数；别处（含屏上名单、导入、报表）不许再辟第二个能出这个结果的入口。
 * 查界的道另起名目（/browse），与归层判档的道分开。
 *
 * @author fuce
 * @date 2026-10-04
 */
@Api(value = "运行界次判定")
@Controller
@RequestMapping("/vehRunLine")
public class VehRunLineController extends BaseController {

    /** 问话那一刻的墙钟写法（与界册落库同一套说法，避开 JDBC 时区不对称） */
    private static final String FMT_TS = "yyyy-MM-dd HH:mm:ss";
    private static final String FMT_DAY = "yyyy-MM-dd";

    @Autowired
    private ITVehRunLineService vehRunLineService;

    @Log(title = "界次归层裁决", action = "verdict")
    @ApiOperation(value = "界次归层（唯一裁决口）",
            notes = "报来的数按车上端口径给（车速 m/s、时长分钟），由服务层折到界册那一头再归层；"
                    + "一条界都够不上时结论写无可用界，界次代号与起用日两栏留空")
    @GetMapping("/verdict")
    @RequiresPermissions("veh:vehRunLine:verdict")
    @ResponseBody
    public AjaxResult verdict(String measureKind, BigDecimal rawValue, String at) {
        String measure = normalizeMeasure(measureKind);
        if (measure == null) {
            return error("所量类别念得不对：只认 speed（车速）与 duration（连续驾驶时长）");
        }
        if (rawValue == null || rawValue.compareTo(BigDecimal.ZERO) < 0) {
            return error("车上报回来的数缺失或小于零，没法取数");
        }
        Date moment = parseWallClock(at);
        if (moment == null) {
            return error("问话的那一刻缺失或写法不对，认 " + FMT_TS + " 或 " + FMT_DAY);
        }
        // 屏上只递话：折算、请界、说话、归层全在服务层一处，这里不许再算第二遍
        RunLineVerdict result = vehRunLineService.verdict(measure, rawValue, moment);
        if (result == null) {
            return error("裁决参数不全，没得出结论");
        }
        return AjaxResult.success(200, "裁决办结", result);
    }

    @Log(title = "在位界名单", action = "available")
    @ApiOperation(value = "那一刻的在位界名单",
            notes = "与裁决请界出自同一处逐条走册：起用已够着、交班未到（交班空着算一直在位）、"
                    + "未停用、分界数站得住；已停用/已交班的不挂")
    @GetMapping("/available")
    @RequiresPermissions("veh:vehRunLine:available")
    @ResponseBody
    public AjaxResult available(String measureKind, String at) {
        String measure = normalizeMeasure(measureKind);
        if (measure == null) {
            return error("所量类别念得不对：只认 speed 与 duration");
        }
        Date moment = parseWallClock(at);
        if (moment == null) {
            return error("问话的那一刻缺失或写法不对，认 " + FMT_TS + " 或 " + FMT_DAY);
        }
        List<TVehRunLine> list = vehRunLineService.listEffective(measure, moment);
        return AjaxResult.success(200, "在位界" + list.size() + "条（按说话次序排）", list);
    }

    @Log(title = "界册翻检", action = "browse")
    @ApiOperation(value = "查界（翻检道，另起名目）",
            notes = "册里翻得着的都摆得出，含已停用、已交班的旧界；这条路只许看，旧界不许捞去比数")
    @GetMapping("/browse")
    @RequiresPermissions("veh:vehRunLine:browse")
    @ResponseBody
    public AjaxResult browse(String measureKind) {
        // measureKind 不传则两类都翻；传了只认 speed/duration
        String measure = normalizeMeasure(measureKind);
        if (measureKind != null && !measureKind.trim().isEmpty() && measure == null) {
            return error("所量类别念得不对：只认 speed 与 duration");
        }
        List<TVehRunLine> list = vehRunLineService.browseRegister(measure);
        return AjaxResult.success(200, "界册翻检（含已停用、已交班，只供翻阅）", list);
    }

    @Log(title = "界册体检", action = "inspect")
    @ApiOperation(value = "界册体检",
            notes = "分界数哪一格空着、哪一格没越过前头那一格，指名道姓一条条报出")
    @GetMapping("/inspect")
    @RequiresPermissions("veh:vehRunLine:inspect")
    @ResponseBody
    public AjaxResult inspect(String measureKind) {
        String measure = normalizeMeasure(measureKind);
        if (measureKind != null && !measureKind.trim().isEmpty() && measure == null) {
            return error("所量类别念得不对：只认 speed 与 duration");
        }
        List<RunLineDefect> defects = vehRunLineService.inspectRegister(measure);
        return defects.isEmpty()
                ? AjaxResult.success("界册逐条走了一遍，分界数都站得住")
                : AjaxResult.success(200, "挑出" + defects.size() + "条站不住的界", defects);
    }

    @Log(title = "屏挂悬空代号核对", action = "dangling")
    @ApiOperation(value = "屏挂代号核对",
            notes = "屏上挂着的界次代号逐个回册里点：那一条线、那一刻请不出在位界的一条条指出缘故")
    @GetMapping("/dangling")
    @RequiresPermissions("veh:vehRunLine:inspect")
    @ResponseBody
    public AjaxResult dangling(String measureKind,
                               @RequestParam(value = "ruleCode", required = false) List<String> ruleCodes,
                               String at) {
        String measure = normalizeMeasure(measureKind);
        if (measureKind != null && !measureKind.trim().isEmpty() && measure == null) {
            return error("所量类别念得不对：只认 speed 与 duration");
        }
        Date moment = parseWallClock(at);
        if (moment == null) {
            return error("问话的那一刻缺失或写法不对，认 " + FMT_TS + " 或 " + FMT_DAY);
        }
        List<String> codes = ruleCodes == null ? new ArrayList<String>() : ruleCodes;
        List<RunLineDefect> dangling = vehRunLineService.danglingScreenCodes(measure, codes, moment);
        return dangling.isEmpty()
                ? AjaxResult.success("屏上挂的代号那一刻都请得出在位界")
                : AjaxResult.success(200, "悬空" + dangling.size() + "条，当场指出", dangling);
    }

    @Log(title = "裁决留痕在册", action = "archive")
    @ApiOperation(value = "裁决归档翻阅",
            notes = "判完即归档在册的那一册：说话界与分界数照裁决那一刻快照，追溯只认这一份，"
                    + "不许拿最新版阈值重算旧账。可按所量类别、问话时刻区间、界次代号翻")
    @GetMapping("/archive")
    @RequiresPermissions("veh:vehRunLine:browse")
    @ResponseBody
    public AjaxResult archive(String measureKind, String from, String to, String ruleCode) {
        String measure = normalizeMeasure(measureKind);
        if (measureKind != null && !measureKind.trim().isEmpty() && measure == null) {
            return error("所量类别念得不对：只认 speed 与 duration");
        }
        Date fromAt = parseWallClock(from);
        if (from != null && !from.trim().isEmpty() && fromAt == null) {
            return error("起刻写法不对，认 " + FMT_TS + " 或 " + FMT_DAY);
        }
        Date toAt = parseWallClock(to);
        if (to != null && !to.trim().isEmpty() && toAt == null) {
            return error("止刻写法不对，认 " + FMT_TS + " 或 " + FMT_DAY);
        }
        List<TVehRunLineVerdictRecord> records =
                vehRunLineService.listVerdictArchive(measure, fromAt, toAt, ruleCode);
        return AjaxResult.success(200, "裁决留痕在册" + records.size() + "条（追溯以此份为准）", records);
    }

    /** 所量类别只认两套说法，别的一概不接 */
    private String normalizeMeasure(String measureKind) {
        if (measureKind == null) {
            return null;
        }
        String m = measureKind.trim();
        if (ITVehRunLineService.MEASURE_SPEED.equals(m) || ITVehRunLineService.MEASURE_DURATION.equals(m)) {
            return m;
        }
        return null;
    }

    /**
     * 墙钟字符串解析：只按界册同一套墙钟说法解，不交给 JDBC 绑参
     * （serverTimezone 与本机时区不对称，直接绑 Date 会在切换当日整体错档，实测踩过）。
     */
    private Date parseWallClock(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim();
        String pattern = t.length() <= FMT_DAY.length() ? FMT_DAY : FMT_TS;
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);
        try {
            return sdf.parse(t);
        } catch (ParseException e) {
            return null;
        }
    }
}
