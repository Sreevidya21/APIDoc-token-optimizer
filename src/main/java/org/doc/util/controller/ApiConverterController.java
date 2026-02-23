package org.doc.util.controller;

import org.doc.util.model.ConversionResult;
import org.doc.util.service.PostmanToSwaggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Controller
public class ApiConverterController {

    @Autowired
    private PostmanToSwaggerService service;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/convert")
    public String convert(
            @RequestParam(value = "postmanJson", required = false) String postmanJson,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Model model) {

        try {
            String json = postmanJson;
            if (file != null && !file.isEmpty()) {
                json = new String(file.getBytes(), StandardCharsets.UTF_8);
            }

            if (json == null || json.isBlank()) {
                model.addAttribute("error", "Please provide a Postman collection JSON (paste or upload a file).");
                return "index";
            }

            ConversionResult result = service.convert(json.trim());
            model.addAttribute("result", result);
            model.addAttribute("postmanInput", json.trim());

        } catch (Exception e) {
            model.addAttribute("error", "Conversion failed: " + e.getMessage());
        }

        return "index";
    }
}
