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
        return "main";
    }
    @GetMapping("/omokRoomList")
    public String omokRoomList(Model model) {
        return "omokRoomList";
    }

    @GetMapping("/omokMain")
    public String showOmokMainPage(Model model) {
        return "omokMain";
    }
}
