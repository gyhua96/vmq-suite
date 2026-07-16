package com.vone.mq.service;

import com.vone.mq.dao.SettingDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.Setting;
import com.vone.mq.utils.PasswordUtil;
import com.vone.mq.utils.ResUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdminAuthService {

    @Autowired
    private SettingDao settingDao;
    @Autowired
    private SettingAccessService settingAccessService;

    @Transactional
    public CommonRes login(String user, String pass) {
        Optional<Setting> userSetting = settingAccessService.find(SettingAccessService.KEY_ADMIN_USER);
        Optional<Setting> passSettingOptional = settingAccessService.find(SettingAccessService.KEY_ADMIN_PASS);
        if (!userSetting.isPresent() || !passSettingOptional.isPresent()) {
            return ResUtil.error("系统配置缺失，请先初始化后台账号密码");
        }
        String configuredUser = userSetting.get().getVvalue();
        if (!user.equals(configuredUser)) {
            return ResUtil.error("账号或密码不正确");
        }
        Setting passSetting = passSettingOptional.get();
        String storedPassword = passSetting.getVvalue();
        if (PasswordUtil.isDefaultPassword(storedPassword)) {
            return ResUtil.error("检测到不安全的默认后台密码，请先通过 VMQ_ADMIN_PASSWORD 或数据库更新为强密码");
        }
        if (!PasswordUtil.matches(pass, storedPassword)) {
            return ResUtil.error("账号或密码不正确");
        }
        if (!PasswordUtil.isHash(storedPassword)) {
            passSetting.setVvalue(PasswordUtil.hash(pass));
            settingDao.save(passSetting);
        }

        return ResUtil.success();
    }
}
