package ru.rs.vpndirector.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WebControllerAdvice {

    private static final String APP_VERSION = "0.0.3";

    @ModelAttribute("appVersion")
    public String appVersion() {
        return APP_VERSION;
    }
}




