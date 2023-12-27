package dev.pcm.omokgame.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/omok")
public class OmokController {

    @GetMapping("/main")
    public String showOmokPage(HttpServletRequest request,Model model) {
        model.addAttribute("ip",request.getRemoteAddr());
        return "main";
    }
    @GetMapping("/omokRoomList")
    public String omokRoomList(HttpServletRequest request, Model model) {
        model.addAttribute("ip",request.getRemoteAddr());
        return "omokRoomList";
    }

    @GetMapping("/omokMain")
    public String showOmokMainPage(HttpServletRequest request, Model model) {
        model.addAttribute("ip",request.getRemoteAddr());
        return "omokMain";
    }
}
