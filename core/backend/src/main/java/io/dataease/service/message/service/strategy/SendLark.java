package io.dataease.service.message.service.strategy;

import io.dataease.commons.service.AuthUserService;
import io.dataease.plugins.common.base.domain.SysUserAssist;
import io.dataease.plugins.config.SpringContextUtil;
import io.dataease.plugins.xpack.lark.service.LarkXpackService;
import io.dataease.service.message.service.SendService;
import io.dataease.service.sys.SysUserService;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service("sendLark")
public class SendLark implements SendService {

    @Autowired
    private AuthUserService authUserService;

    @Resource
    private SysUserService sysUserService;

    @Override
    public void sendMsg(Long userId, Long typeId, String content, String param) {
        SysUserAssist sysUserAssist = sysUserService.assistInfo(userId);
        if (ObjectUtils.isNotEmpty(sysUserAssist) && StringUtils.isNotBlank(sysUserAssist.getLarkId()) && authUserService.supportLark()) {
            String username = sysUserAssist.getLarkId();
            LarkXpackService larkXpackService = SpringContextUtil.getBean(LarkXpackService.class);
            List<String> userIds = new ArrayList<>();
            userIds.add(username);
            larkXpackService.pushMsg(userIds, content);
        }
    }
}
