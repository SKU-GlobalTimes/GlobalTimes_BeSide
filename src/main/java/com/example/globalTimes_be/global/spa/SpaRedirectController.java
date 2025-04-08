package com.example.globalTimes_be.global.spa;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaRedirectController {
    @RequestMapping(value = "/{path:^(?!.*\\.).*$}")
    public String redirect() {
        return "forward:/index.html";
    }
}