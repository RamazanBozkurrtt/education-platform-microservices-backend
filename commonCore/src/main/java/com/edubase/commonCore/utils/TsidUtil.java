package com.edubase.commonCore.utils;

import io.hypersistence.tsid.TSID;

public class TsidUtil {

    //This class only generates static methods
    private TsidUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String generateId(){
        return TSID.fast().toString();
    }

    public static Long generateLongId(){
        return TSID.fast().toLong();
    }

}
