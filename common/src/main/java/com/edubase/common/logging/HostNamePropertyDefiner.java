package com.edubase.common.logging;

import ch.qos.logback.core.PropertyDefinerBase;
import com.edubase.common.utils.NetworkUtils;

public class HostNamePropertyDefiner extends PropertyDefinerBase {
    @Override
    public String getPropertyValue() {
        return NetworkUtils.getHostName();
    }
}
