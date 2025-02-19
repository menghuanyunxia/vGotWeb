package io.dataease.template.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.dataease.api.template.TemplateManageApi;
import io.dataease.api.template.dto.TemplateManageDTO;
import io.dataease.api.template.request.TemplateManageRequest;
import io.dataease.api.template.vo.VisualizationTemplateVO;
import io.dataease.constant.CommonConstants;
import io.dataease.exception.DEException;
import io.dataease.template.dao.auto.entity.VisualizationTemplate;
import io.dataease.template.dao.auto.entity.VisualizationTemplateCategory;
import io.dataease.template.dao.auto.entity.VisualizationTemplateCategoryMap;
import io.dataease.template.dao.auto.mapper.VisualizationTemplateCategoryMapMapper;
import io.dataease.template.dao.auto.mapper.VisualizationTemplateCategoryMapper;
import io.dataease.template.dao.auto.mapper.VisualizationTemplateMapper;
import io.dataease.template.dao.ext.ExtVisualizationTemplateMapper;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.BeanUtils;
import io.dataease.visualization.server.StaticResourceServer;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.dataease.constant.StaticResourceConstants.UPLOAD_URL_PREFIX;

/**
 * @author : WangJiaHao
 * @date : 2023/11/7 13:29
 */
@RestController
@RequestMapping("/templateManage")
public class TemplateManageService implements TemplateManageApi {

    @Resource
    private VisualizationTemplateMapper templateMapper;

    @Resource
    private VisualizationTemplateCategoryMapper templateCategoryMapper;
    @Resource
    private VisualizationTemplateCategoryMapMapper categoryMapMapper;
    @Resource
    private ExtVisualizationTemplateMapper extTemplateMapper;
    @Resource
    private StaticResourceServer staticResourceServer;

    @Override
    public List<TemplateManageDTO> templateList(TemplateManageRequest request) {
        request.setWithBlobs("N");
        List<TemplateManageDTO> templateList = extTemplateMapper.findTemplateList(request);
        if (request.getWithChildren()) {
            getTreeChildren(templateList,request.getLeafDvType());
        }
        return templateList;
    }

    public void getTreeChildren(List<TemplateManageDTO> parentTemplateList,String dvType) {
        Optional.ofNullable(parentTemplateList).ifPresent(parent -> parent.forEach(parentTemplate -> {
            List<TemplateManageDTO> panelTemplateDTOChildren = extTemplateMapper.findTemplateList(new TemplateManageRequest(parentTemplate.getId(),dvType));
            parentTemplate.setChildren(panelTemplateDTOChildren);
            getTreeChildren(panelTemplateDTOChildren,dvType);
        }));
    }

    public List<TemplateManageDTO> getSystemTemplateType(TemplateManageRequest request) {
        return extTemplateMapper.findTemplateList(request);
    }


    @Transactional
    @Override
    public TemplateManageDTO save(TemplateManageRequest request) {
        if (StringUtils.isEmpty(request.getId())) {
            request.setId(UUID.randomUUID().toString());
            request.setCreateTime(System.currentTimeMillis());
            request.setCreateBy(AuthUtils.getUser().getUserId().toString());
            if ("template".equals(request.getNodeType())) {
                //Store static resource into the server
                staticResourceServer.saveFilesToServe(request.getStaticResource());
                String snapshotName = "template-" + request.getId() + ".jpeg";
                staticResourceServer.saveSingleFileToServe(snapshotName, request.getSnapshot().replace("data:image/jpeg;base64,", ""));
                request.setSnapshot("/" + UPLOAD_URL_PREFIX + '/' + snapshotName);
            }
            //如果level 是0（第一级）指的是分类目录 设置父级为对应的templateType
            if (request.getLevel() == 0) {
                request.setPid(request.getTemplateType());
                String nameCheckResult = this.categoryNameCheck(CommonConstants.OPT_TYPE.INSERT, request.getName(), null);
                if (CommonConstants.CHECK_RESULT.EXIST_ALL.equals(nameCheckResult)) {
                    DEException.throwException("名称已存在");
                }
                VisualizationTemplateCategory templateCategory = new VisualizationTemplateCategory();
                BeanUtils.copyBean(templateCategory,request);
                templateCategoryMapper.insert(templateCategory);
            } else {//模板插入 同名的模板进行覆盖(先删除)
                QueryWrapper<VisualizationTemplate> wrapper = new QueryWrapper<>();
                wrapper.eq("name",request.getName());
                templateMapper.delete(wrapper);
                VisualizationTemplate template = new VisualizationTemplate();
                BeanUtils.copyBean(template,request);
                templateMapper.insert(template);
                // 插入分类关系
                request.getCategories().forEach(categoryId ->{
                    VisualizationTemplateCategoryMap categoryMap = new VisualizationTemplateCategoryMap();
                    categoryMap.setId(UUID.randomUUID().toString());
                    categoryMap.setCategoryId(categoryId);
                    categoryMap.setTemplateId(template.getId());
                    categoryMapMapper.insert(categoryMap);
                });

            }
        } else {
            if (request.getLevel() == 0) {
                String nameCheckResult = this.categoryNameCheck(CommonConstants.OPT_TYPE.UPDATE, request.getName(), request.getId());
                if (CommonConstants.CHECK_RESULT.EXIST_ALL.equals(nameCheckResult)) {
                    DEException.throwException("名称已存在");
                }
                VisualizationTemplateCategory templateCategory = new VisualizationTemplateCategory();
                BeanUtils.copyBean(templateCategory,request);
                templateCategoryMapper.updateById(templateCategory);
            }else{
                String nameCheckResult = this.nameCheck(CommonConstants.OPT_TYPE.UPDATE, request.getName(), request.getId());
                if (CommonConstants.CHECK_RESULT.EXIST_ALL.equals(nameCheckResult)) {
                    DEException.throwException("名称已存在");
                }
                VisualizationTemplate template = new VisualizationTemplate();
                BeanUtils.copyBean(template,request);
                templateMapper.updateById(template);
            }

        }
        TemplateManageDTO templateManageDTO = new TemplateManageDTO();
        BeanUtils.copyBean(templateManageDTO, request);
        templateManageDTO.setLabel(request.getName());
        return templateManageDTO;
    }

    //名称检查
    public String nameCheck(String optType, String name,String id) {
        QueryWrapper<VisualizationTemplate> wrapper = new QueryWrapper<>();
        if (CommonConstants.OPT_TYPE.INSERT.equals(optType)) {
            wrapper.eq("name",name);
        } else if (CommonConstants.OPT_TYPE.UPDATE.equals(optType)) {
            wrapper.eq("name",name);
            wrapper.ne("id",id);
        }
        List<VisualizationTemplate> templateList = templateMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(templateList)) {
            return CommonConstants.CHECK_RESULT.NONE;
        } else {
            return CommonConstants.CHECK_RESULT.EXIST_ALL;
        }
    }

    //名称检查
    public String categoryNameCheck(String optType, String name, String id) {
        QueryWrapper<VisualizationTemplateCategory> wrapper = new QueryWrapper<>();
        if (CommonConstants.OPT_TYPE.INSERT.equals(optType)) {
            wrapper.eq("name",name);
        } else if (CommonConstants.OPT_TYPE.UPDATE.equals(optType)) {
            wrapper.eq("name",name);
            wrapper.ne("id",id);
        }
        List<VisualizationTemplateCategory> templateList = templateCategoryMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(templateList)) {
            return CommonConstants.CHECK_RESULT.NONE;
        } else {
            return CommonConstants.CHECK_RESULT.EXIST_ALL;
        }
    }
    @Override
    public String nameCheck(TemplateManageRequest request) {
        return nameCheck(request.getOptType(), request.getName(), request.getId());
    }
    @Override
    public void delete(String id) {
        Assert.notNull(id, "id cannot be null");
        templateMapper.deleteById(id);
    }
    @Override
    public void deleteCategory(String id) {
        Assert.notNull(id, "id cannot be null");
        templateCategoryMapper.deleteById(id);
    }
    @Override
    public VisualizationTemplateVO findOne(String templateId) {
        VisualizationTemplate template = templateMapper.selectById(templateId);
        if(template != null){
            VisualizationTemplateVO templateVO = new VisualizationTemplateVO();
            BeanUtils.copyBean(templateVO,template);
            return templateVO;
        }else{
            return null;
        }
    }
    @Override
    public List<TemplateManageDTO> find(TemplateManageRequest request) {
        return extTemplateMapper.findTemplateList(request);
    }

    @Override
    public List<TemplateManageDTO> findCategories(TemplateManageRequest request) {
        return extTemplateMapper.findCategories(request);
    }
}
