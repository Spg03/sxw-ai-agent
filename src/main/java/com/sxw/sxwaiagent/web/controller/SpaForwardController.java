package com.sxw.sxwaiagent.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards known SPA routes to index.html so React Router handles client-side routing.
 * Only whitelists specific frontend paths; API/actuator/swagger paths are unaffected.
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {
        "/chat", "/chat/**",
        "/treehole", "/treehole/**",
        "/notes", "/notes/**",
        "/eval", "/eval/**",
        "/skills", "/skills/**",
        "/traces", "/traces/**",
        "/dashboard", "/dashboard/**",
        "/login"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
