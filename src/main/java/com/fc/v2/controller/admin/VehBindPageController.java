package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TVehBindPage;
import com.fc.v2.model.custom.VehBindBoard;
import com.fc.v2.model.custom.VehBindForm;
import com.fc.v2.model.custom.VehBindQuery;
import com.fc.v2.service.ITVehBindPageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 车辆联网清单页 Controller
 *
 * 这一层只做传参与回显：立不立得住、还差几项轧出几、该见哪几页、页头三数打哪儿来，
 * 四样全归业务服务层那一条写入/开屏方法作答，此处不留第二处判路。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "车辆联网清单页")
@Controller
@RequestMapping("/VehBindPageController")
public class VehBindPageController extends BaseController {

    private final String prefix = "admin/vehBindPage";

    @Autowired
    private ITVehBindPageService vehBindPageService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("veh:vehBindPage:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "车辆联网清单页开屏", action = "board")
    @ApiOperation(value = "开屏分页查询",
            notes = "页头三栏与分页名单同一次算成；县岗不见邻县、市域不掺别市；本层没立过页就是三栏零、名单空着")
    @GetMapping("/list")
    @RequiresPermissions("veh:vehBindPage:list")
    @ResponseBody
    public ResultTable list(VehBindQuery query) {
        VehBindBoard board = vehBindPageService.openBoard(query);
        ResultTable table = ResultTable.pageTable(board.getTotalCount(), board.getPages());
        // 页头三栏照业务层同一回点算的结果摆，接口层不另点一遍
        table.setBindTotal(board.getTotalCount());
        table.setBindLinked(board.getLinkedCount());
        table.setBindLack(board.getLackCount());
        return table;
    }

    @ApiOperation(value = "新增跳转", notes = "一车一张亲手立与从当月核收里带出，同一张表单、同一种样子")
    @GetMapping("/add")
    @RequiresPermissions("veh:vehBindPage:add")
    public String add(@RequestParam(required = false) String roadPrefix,
                      @RequestParam(required = false) Integer bring, ModelMap model) {
        // 立页选业户：只摆本地这一路上仍在册的，已销户的不摆进来
        model.put("roster", vehBindPageService.listRoster(roadPrefix));
        model.put("form", new VehBindForm());
        // bring=1 只是换个提交口子（同义方法），界面上不出现第二种样子
        model.put("bring", bring != null && bring == 1);
        return prefix + "/add";
    }

    @Log(title = "车辆联网清单页新增", action = "add")
    @ApiOperation(value = "新增", notes = "一车一张亲手立；清单代号、签认码、还差几项页上不摆录入框，全由系统出")
    @PostMapping("/add")
    @RequiresPermissions("veh:vehBindPage:add")
    @ResponseBody
    public AjaxResult add(VehBindForm form) {
        return toAjax(vehBindPageService.saveFromForm(form));
    }

    @Log(title = "从当月核收带出立页", action = "bring")
    @ApiOperation(value = "从当月核收里带出立页",
            notes = "立页第二条路子：另起同义方法接住，数法与亲手立同一套，界面上也只有一种样子")
    @PostMapping("/bringFromCheck")
    @RequiresPermissions("veh:vehBindPage:add")
    @ResponseBody
    public AjaxResult bringFromCheck(VehBindForm form) {
        return toAjax(vehBindPageService.bringFromMonthlyCheck(form));
    }

    @ApiOperation(value = "修改跳转", notes = "修改跳转")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("veh:vehBindPage:edit")
    public String edit(@PathVariable("id") Long id, @RequestParam(required = false) String roadPrefix,
                       ModelMap model) {
        TVehBindPage page = vehBindPageService.selectTVehBindPageById(id);
        VehBindForm form = new VehBindForm();
        if (page != null) {
            form.setId(page.getId());
            form.setVehCode(page.getVehCode());
            form.setSiteId(page.getSiteId());
            form.setSiteNo(page.getSiteNo());
            form.setNodeNo(page.getNodeNo());
            form.setShouldCountRaw(page.getShouldCount() == null ? null : String.valueOf(page.getShouldCount()));
            form.setDoneCountRaw(page.getDoneCount() == null ? null : String.valueOf(page.getDoneCount()));
            form.setContent(page.getContent());
            form.setRemark(page.getRemark());
        }
        model.put("roster", vehBindPageService.listRoster(roadPrefix));
        model.put("form", form);
        if (page != null) {
            // 改看时业户全称回名录照查；已销户业户的旧页也要显出全称
            com.fc.v2.model.auto.TVehEnt ent = vehBindPageService.selectEntById(page.getSiteId());
            if (ent != null) {
                page.setSiteName(ent.getSiteName());
                page.setSiteType(ent.getSiteType());
                page.setRoadName(ent.getRoadName());
            }
        }
        model.put("page", page);
        return prefix + "/edit";
    }

    @Log(title = "车辆联网清单页修改", action = "edit")
    @ApiOperation(value = "修改保存",
            notes = "已联项数改口、页换挂靠那一层都在此重轧；轧出的新数覆盖旧数，这一格只回显")
    @PostMapping("/edit")
    @RequiresPermissions("veh:vehBindPage:edit")
    @ResponseBody
    public AjaxResult editSave(VehBindForm form) {
        return toAjax(vehBindPageService.saveFromForm(form));
    }

    @Log(title = "车辆联网清单页删除", action = "remove")
    @ApiOperation(value = "删除", notes = "留痕删除，在场的页不许被顺手抹掉")
    @DeleteMapping("/remove")
    @RequiresPermissions("veh:vehBindPage:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(vehBindPageService.deleteTVehBindPageByIds(ids));
    }
}
