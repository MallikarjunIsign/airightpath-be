package com.rightpath.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorPageController {

    @GetMapping("/error/invalid-request")
    public String errorPage() {
        // returns templates/invalid-request.html
        return "invalid-request";
    }
}
