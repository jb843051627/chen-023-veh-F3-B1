package com.fc.v2.controller.admin;

import java.util.Calendar;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TVehAlarmBatch;
import com.fc.v2.model.auto.TVehAlarmRow;
import com.fc.v2.model.custom.VehAlarmImport;
import com.fc.v2.model.custom.VehAlarmReceipt;
import com.fc.v2.service.ITVehAlarmBatchService;
import com.fc.v2.service.ITVehAlarmRowService;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 月度报警处置核收册 Controller（整册进门，一道口子）
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "月度报警处置核收册")
@Controller
@RequestMapping("/vehAlarmBatch")
public class VehAlarmBatchController extends BaseController {

    private final String prefix = "admin/vehAlarmBatch";

    @Autowired
    private ITVehAlarmBatchService vehAlarmBatchService;

    @Autowired
    private ITVehAlarmRowService vehAlarmRowService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("veh:vehAlarmBatch:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "月度报警处置核收册集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("veh:vehAlarmBatch:list")
    @ResponseBody
    public ResultTable list(TVehAlarmBatch record) {
        QueryWrapper<TVehAlarmBatch> queryWrapper = new QueryWrapper<TVehAlarmBatch>();
        queryWrapper.eq("del_flag", 0);
        if (StringUtils.isNotEmpty(record.getBatchNo())) {
            queryWrapper.eq("batch_no", record.getBatchNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        if (record.getSourceKind() != null) {
            queryWrapper.eq("source_kind", record.getSourceKind());
        }
        queryWrapper.orderByDesc("id");
        startPage();
        com.github.pagehelper.PageInfo<TVehAlarmBatch> page =
                new com.github.pagehelper.PageInfo<TVehAlarmBatch>(vehAlarmBatchService.selectTVehAlarmBatchList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "核收册行查询", action = "rows")
    @ApiOperation(value = "册下各行", notes = "layerFlag不传看现行，传1翻往期；往期照旧翻得着")
    @GetMapping("/rows")
    @RequiresPermissions("veh:vehAlarmBatch:list")
    @ResponseBody
    public ResultTable rows(String batchNo, Integer layerFlag) {
        startPage();
        com.github.pagehelper.PageInfo<TVehAlarmRow> page =
                new com.github.pagehelper.PageInfo<TVehAlarmRow>(vehAlarmBatchService.listRows(batchNo, layerFlag));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "整册导入", action = "import")
    @ApiOperation(value = "整册导入", notes = "一册一次一次性进门；机构自录与各市转递同走这一道门")
    @PostMapping("/import")
    @RequiresPermissions("veh:vehAlarmBatch:import")
    @ResponseBody
    public AjaxResult importBatch(@RequestBody VehAlarmImport param) {
        String no = param.getBatchNo();
        if (StringUtils.isEmpty(no) && param.getRows() != null && !param.getRows().isEmpty()) {
            no = param.getRows().get(0).getBatchNo();
        }
        if (StringUtils.isEmpty(no)) {
            return error("册码缺失，没法落册");
        }
        int r = vehAlarmBatchService.importBatch(no, param.getSourceKind(), param.getReportYear(),
                param.getReportMonth(), param.getRows());
        if (r < 0) {
            return error("册上行数压过处里定的顶，整册原样退回，库里一行不留");
        }
        // 办结即出回执，三个数回库现点现取
        return success(200, "整册导入办结", vehAlarmBatchService.receipt(no));
    }

    @Log(title = "回执现点", action = "receipt")
    @ApiOperation(value = "回执", notes = "册上原有几行、落进库里几行、原样送回几行，各自回库现点现取")
    @GetMapping("/receipt")
    @RequiresPermissions("veh:vehAlarmBatch:receipt")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        VehAlarmReceipt receipt = vehAlarmBatchService.receipt(batchNo);
        return success(200, "回执现点现取", receipt);
    }

    @Log(title = "回执一致性核对", action = "check")
    @ApiOperation(value = "回执一致性核对", notes = "行上结果与回执数差开的，当场报出来")
    @GetMapping("/check")
    @RequiresPermissions("veh:vehAlarmBatch:check")
    @ResponseBody
    public AjaxResult check(String batchNo) {
        List<TVehAlarmRow> diff = vehAlarmBatchService.checkReceipt(batchNo);
        if (diff.isEmpty()) {
            return success("行上结果与回执三数出自同一回点算，接得平");
        }
        return success(200, "差开" + diff.size() + "行，当场报出", diff);
    }

    @Log(title = "撤册", action = "withdraw")
    @ApiOperation(value = "撤册", notes = "要改册先办撤册，撤完册码才让重递")
    @PostMapping("/withdraw")
    @RequiresPermissions("veh:vehAlarmBatch:withdraw")
    @ResponseBody
    public AjaxResult withdraw(String batchNo) {
        int r = vehAlarmBatchService.withdrawBatch(batchNo);
        return r > 0 ? success("撤册办结，册码可重递") : error("无此在办册，撤册未办成");
    }

    @Log(title = "核讫", action = "finalize")
    @ApiOperation(value = "核讫", notes = "核讫只给有行数的册发，空册不许发核讫")
    @PostMapping("/finalize")
    @RequiresPermissions("veh:vehAlarmBatch:finalize")
    @ResponseBody
    public AjaxResult finalizeBatch(String batchNo) {
        int r = vehAlarmBatchService.finalizeBatch(batchNo);
        if (r > 0) {
            return success("核讫");
        }
        return r < 0 ? error("空册不发核讫") : error("无此在办册，核讫未办成");
    }

    @Log(title = "往期迁移", action = "archive")
    @ApiOperation(value = "往期迁移", notes = "连着两个报送年度还没着落的未勾行挪到往期层，只朝旧册开")
    @PostMapping("/archive")
    @RequiresPermissions("veh:vehAlarmBatch:archive")
    @ResponseBody
    public AjaxResult archive(Integer currentYear) {
        int year = currentYear != null ? currentYear : Calendar.getInstance().get(Calendar.YEAR);
        int n = vehAlarmBatchService.archiveStaleRows(year);
        return success("挪到往期" + n + "行");
    }

    @Log(title = "行勾选", action = "checkRow")
    @ApiOperation(value = "行勾选", notes = "只许从未勾落笔，勾成收下或退回；退回须写缘故")
    @PostMapping("/checkRow")
    @RequiresPermissions("veh:vehAlarmBatch:checkRow")
    @ResponseBody
    public AjaxResult checkRow(Long id, Integer status, String remark) {
        int r = vehAlarmRowService.checkRow(id, status, remark);
        return r > 0 ? success() : error("行不在未勾或缘故没落，勾不成");
    }
}
