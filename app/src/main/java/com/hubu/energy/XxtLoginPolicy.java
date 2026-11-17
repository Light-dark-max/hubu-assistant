package com.hubu.energy;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Login assets have their own policy; they are not collector endpoints. */
final class XxtLoginPolicy {
    static final Set<String> RESOURCE_HOSTS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "passport2-static.chaoxing.com", "homewh.chaoxing.com", "captcha.chaoxing.com")));

    static boolean allowed(String url) {
        if(XxtClient.allowed(url))return true;
        try {
            URI u=new URI(url);
            return "https".equalsIgnoreCase(u.getScheme())&&RESOURCE_HOSTS.contains(u.getHost())
                &&(u.getPort()==-1||u.getPort()==443)&&u.getRawUserInfo()==null;
        }catch(Exception e){return false;}
    }

    static boolean captcha(String url) {
        try{return allowed(url)&&"captcha.chaoxing.com".equals(new URI(url).getHost());}
        catch(Exception e){return false;}
    }
}
