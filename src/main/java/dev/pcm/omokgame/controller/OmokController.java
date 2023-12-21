package dev.pcm.omokgame.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/omok")
public class OmokController {

    @GetMapping("/main")
    public String showOmokPage(Model model) {
        model.addAttribute("playerName", "John Doe");
        model.addAttribute("score", 100);

        return "omokMain";
    }
}