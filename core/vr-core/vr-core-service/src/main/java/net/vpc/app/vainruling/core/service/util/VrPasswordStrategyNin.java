package net.vpc.app.vainruling.core.service.util;

import net.vpc.app.vainruling.core.service.model.AppContact;
import net.vpc.common.strings.StringUtils;

public class VrPasswordStrategyNin implements VrPasswordStrategy {
    public static final VrPasswordStrategy INSTANCE = new VrPasswordStrategyNin();

    @Override
    public String generatePassword(AppContact contact) {
        String nin = contact.getNin();
        if (StringUtils.isEmpty(nin)) {
            throw new IllegalArgumentException("Empty NIN, unable to generate password");
        }
        return nin.trim();
    }
}
