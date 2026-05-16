package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.mail.GmailAliasService;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/oauth/gmail")
public class GmailOAuthController {

    private final GmailAliasService gmailAliasService;

    public GmailOAuthController(GmailAliasService gmailAliasService) {
        this.gmailAliasService = gmailAliasService;
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam String code, @RequestParam String state) {
        gmailAliasService.exchangeCodeAndSyncAlias(UUID.fromString(state), code);
        return new RedirectView("/app");
    }
}
