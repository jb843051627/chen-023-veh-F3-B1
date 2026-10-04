package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TVehHandleFlow;
import com.fc.v2.service.ITVehHandleFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 严重报警处置单 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "严重报警处置单")
@Controller
@RequestMapping("/vehHandleFlow")
public class VehHandleFlowController extends BaseController {

    private final String prefix = "admin/vehHandleFlow";

    @Autowired
    private ITVehHandleFlowService vehHandleFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("vehHandleFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "严重报警处置单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("vehHandleFlow:list")
    @ResponseBody
    public ResultTable list(TVehHandleFlow record) {
        QueryWrapper<TVehHandleFlow> queryWrapper = new QueryWrapper<TVehHandleFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TVehHandleFlow> page =
                new com.github.pagehelper.PageInfo<TVehHandleFlow>(vehHandleFlowService.selectTVehHandleFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "严重报警处置单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("vehHandleFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(vehHandleFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "严重报警处置单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("vehHandleFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(vehHandleFlowService.rollback(id, remark) != null ? 1 : 0);
    }
}
