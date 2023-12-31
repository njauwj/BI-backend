package com.yupi.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AIManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.mq.MqProducer;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.utils.SqlUtils;
import io.netty.util.concurrent.CompleteFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static com.yupi.springbootinit.constant.AIConstant.MODEL_ID;

/**
 * 帖子接口
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AIManager aiManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private MqProducer mqProducer;

    private final static Gson GSON = new Gson();

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size), getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size), getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    /**
     * 智能分析,生成图表
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        Long userId = userService.getLoginUser(request).getId();
        //实现用户请求的限流
        redisLimiterManager.allowRequest("genChartByAi:" + userId);
        long size = multipartFile.getSize();
        int maxSize = 1024 * 1024;//1Mb
        ThrowUtils.throwIf(size > maxSize, ErrorCode.PARAMS_ERROR, "文件太大");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        List<String> validSuffix = Arrays.asList(".xls", "xlsx", "csv");
        ThrowUtils.throwIf(!validSuffix.contains(suffix), ErrorCode.PARAMS_ERROR, "上传文件格式错误");
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        // 用户输入
        StringBuilder userInput = new StringBuilder();
        if (StringUtils.isNotBlank(chartType)) {
            goal = "使用" + chartType + "," + goal;
        }
        userInput.append("分析需求:\n").append(goal).append("\n");
        // 压缩后的数据
        String chartData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append("原始数据:\n").append(chartData).append("\n");

        String[] message = aiManager.doChat(MODEL_ID, userInput.toString()).split("【【【【【");
        ThrowUtils.throwIf(message.length != 3, ErrorCode.PARAMS_ERROR, "AI生成数据失败");
        String chatCode = message[1];
        String chatConclusion = message[2];
        Long chartId = addChartToDb(userId, genChartByAiRequest, chartData, chatCode, chatConclusion);
        BiResponse biResponse = new BiResponse(chatCode, chatConclusion, chartId);
        return ResultUtils.success(biResponse);
    }

    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        Long userId = userService.getLoginUser(request).getId();
        //实现用户请求的限流
        redisLimiterManager.allowRequest("genChartByAi:" + userId);
        long size = multipartFile.getSize();
        int maxSize = 1024 * 1024;//1Mb
        ThrowUtils.throwIf(size > maxSize, ErrorCode.PARAMS_ERROR, "文件太大");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        List<String> validSuffix = Arrays.asList(".xls", "xlsx", "csv");
        ThrowUtils.throwIf(!validSuffix.contains(suffix), ErrorCode.PARAMS_ERROR, "上传文件格式错误");
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 用户输入
        StringBuilder userInput = new StringBuilder();
        if (StringUtils.isNotBlank(chartType)) {
            goal = "使用" + chartType + "," + goal;
        }
        userInput.append("分析需求:\n").append(goal).append("\n");
        // 压缩后的数据
        String chartData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append("原始数据:\n").append(chartData).append("\n");
        Chart chart = new Chart();
        chart.setName(genChartByAiRequest.getName());
        chart.setGoal(goal);
        chart.setChartData(chartData);
        chart.setChartType(genChartByAiRequest.getChartType());
        chart.setUserId(userId);
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.PARAMS_ERROR, "图表生成任务添加到数据库失败");
        //异步执行
        CompletableFuture.runAsync(() -> {
            try {
                chart.setStatus("running");
                chartService.updateById(chart);
                String[] message = aiManager.doChat(MODEL_ID, userInput.toString()).split("【【【【【");
                ThrowUtils.throwIf(message.length != 3, ErrorCode.PARAMS_ERROR, "生成数据失败请重试");
                String chatCode = message[1];
                String chatConclusion = message[2];
                chart.setGenChart(chatCode);
                chart.setGenResult(chatConclusion);
                chart.setStatus("succeed");
                chartService.updateById(chart);
            } catch (Exception e) {
                chart.setStatus("failed");
                chart.setExecMessage("AI生成图表失败");
                chartService.updateById(chart);
            }
        }, threadPoolExecutor);
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }


    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        Long userId = userService.getLoginUser(request).getId();
        //实现用户请求的限流
        redisLimiterManager.allowRequest("genChartByAi:" + userId);
        long size = multipartFile.getSize();
        int maxSize = 1024 * 1024;//1Mb
        ThrowUtils.throwIf(size > maxSize, ErrorCode.PARAMS_ERROR, "文件太大");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        List<String> validSuffix = Arrays.asList(".xls", "xlsx", "csv");
        ThrowUtils.throwIf(!validSuffix.contains(suffix), ErrorCode.PARAMS_ERROR, "上传文件格式错误");
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 用户输入
        if (StringUtils.isNotBlank(chartType)) {
            goal = "使用" + chartType + "," + goal;
        }
        // 压缩后的数据
        String chartData = ExcelUtils.excelToCsv(multipartFile);
        Chart chart = new Chart();
        chart.setName(genChartByAiRequest.getName());
        chart.setGoal(goal);
        chart.setChartData(chartData);
        chart.setChartType(genChartByAiRequest.getChartType());
        chart.setUserId(userId);
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.PARAMS_ERROR, "图表生成任务添加到数据库失败");
        //发送消息
        mqProducer.sendMessage(chart.getId().toString());
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    private long addChartToDb(Long userId, GenChartByAiRequest genChartByAiRequest, String chartData, String genChart, String genResult) {
        Chart chart = new Chart();
        chart.setName(genChartByAiRequest.getName());
        chart.setGoal(genChartByAiRequest.getGoal());
        chart.setChartData(chartData);
        chart.setChartType(genChartByAiRequest.getChartType());
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(userId);
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "保存图表至数据库失败");
        return chart.getId();
    }


    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        return queryWrapper;
    }


}
